package ca.jdsecurity.incidents.database;

import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {
    Connection con;
    CityOfWinnipegService cityOfWinnipegService;
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    public Database(String cityOfWinnipegSecret, String cityOfWinnipegHost, String cityOfWinnipegPath, String cityOfWinnipegQuery) throws SQLException {
        this.con = getConnection();
        this.cityOfWinnipegService = new CityOfWinnipegService(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
        createIncidentsTable();
    }

    public Connection getConnection() throws SQLException {
        String urlConnection = "jdbc:derby:incidents;create=true";
        return DriverManager.getConnection(urlConnection);
    }

    public void createIncidentsTable() throws SQLException {
        log.info("Creating Incident Table");
        String sql = "CREATE TABLE incidents (incident_number VARCHAR(255) PRIMARY KEY,incident_type VARCHAR(255),is_motor VARCHAR(255),units VARCHAR(255),neighbourhood VARCHAR(255),ward VARCHAR(255), call_time VARCHAR(255))";
        try (Statement statement = con.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            // Derby SQLState for "table already exists"
            if (!"X0Y32".equals(e.getSQLState())) {
                throw e;
            }
            log.info("Incident table already exists");
        }
    }

    public void syncIncidentsTable() throws SQLException, UnirestException, JsonProcessingException {
        log.info("Starting City of Winnipeg Incident Sync");
        try (Statement statement = con.createStatement()) {
            statement.execute("DELETE FROM incidents");
        }

        List<HashMap<String, Object>> incidentListing = this.cityOfWinnipegService.getAllIncidents();
        try (PreparedStatement preparedStatement = con.prepareStatement(
                "INSERT INTO incidents VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (Map<String, Object> incident : incidentListing) {
                preparedStatement.setString(1, (String) incident.get("incident_number"));
                preparedStatement.setString(2, (String) incident.get("incident_type"));
                preparedStatement.setString(3, (String) incident.get("motor_vehicle_incident"));
                preparedStatement.setString(4, (String) incident.get("units"));
                preparedStatement.setString(5, (String) incident.get("neighbourhood"));
                preparedStatement.setString(6, (String) incident.get("ward"));
                preparedStatement.setString(7, (String) incident.get("call_time"));
                preparedStatement.execute();
            }
        }
        log.info("City of Winnipeg Incident Sync Completed");

    }

    public List<HashMap<String, Object>> getAllIncidentsFromToday() throws SQLException {
        log.info("Retrieving all Incidents from Database");
        try (Statement statement = con.createStatement();
             ResultSet result = statement.executeQuery("select * from incidents")) {
            ResultSetMetaData md = result.getMetaData();
            int columns = md.getColumnCount();
            List<HashMap<String, Object>> list = new ArrayList<>();

            while (result.next()) {
                HashMap<String, Object> row = new HashMap<>(columns);
                for (int i = 1; i <= columns; ++i) {
                    row.put(md.getColumnName(i), result.getObject(i));
                }
                list.add(row);
            }
            return list;
        }
    }
}
