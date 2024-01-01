package ca.jdsecurity.incidents.database;

import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import ca.jdsecurity.incidents.tasks.ScheduledTasks;
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
        Statement statement = con.createStatement();
        try{
            statement.execute("DROP Table incidents");
        }
        catch(Exception e){}
        String sql = "CREATE TABLE incidents (incident_number VARCHAR(255) PRIMARY KEY,incident_type VARCHAR(255),is_motor VARCHAR(255),units VARCHAR(255),neighbourhood VARCHAR(255),ward VARCHAR(255), call_time VARCHAR(255))";
		statement.execute(sql);

    }

    public void syncIncidentsTable() throws SQLException, UnirestException, JsonProcessingException {
        log.info("Starting City of Winnipeg Incident Sync");
        Statement statement = con.createStatement();
        String sql = "DELETE FROM incidents";
        statement.execute(sql);
        List<HashMap<String, Object>> incidentListing = this.cityOfWinnipegService.getAllIncidents();
        for(Map<String,Object> incident: incidentListing){
            PreparedStatement preparedStatement = con.prepareStatement(
                    "INSERT INTO incidents VALUES (?, ?, ?, ?, ?, ?, ?)"
            );
            preparedStatement.setString(1, (String) incident.get("incident_number"));
            preparedStatement.setString(2, (String) incident.get("incident_type"));
            preparedStatement.setString(3, (String) incident.get("motor_vehicle_incident"));
            preparedStatement.setString(4, (String) incident.get("units"));
            preparedStatement.setString(5, (String) incident.get("neighbourhood"));
            preparedStatement.setString(6, (String) incident.get("ward"));
            preparedStatement.setString(7, (String) incident.get("call_time"));
            preparedStatement.execute();
        }
        log.info("City of Winnipeg Incident Sync Completed");

    }

    public List<HashMap<String, Object>> getAllIncidentsFromToday() throws SQLException {
        log.info("Retrieving all Incidents from Database");
        Statement statement = con.createStatement();
        ResultSet result = statement.executeQuery("select * from incidents");
        ResultSetMetaData md = result.getMetaData();
        int columns = md.getColumnCount();
        List<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>();

        while (result.next()) {
            HashMap<String,Object> row = new HashMap<String, Object>(columns);
            for(int i=1; i<=columns; ++i) {
                row.put(md.getColumnName(i),result.getObject(i));
            }
            list.add(row);
        }
        return list;
    }
}
