package ca.jdsecurity.incidents.tasks;

import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class ScheduledTasks {

    Database database;
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private Environment env;
    String cityOfWinnipegSecret;
    String googleApiKey;
    String cityOfWinnipegHost;
    String cityOfWinnipegPath;
    String cityOfWinnipegQuery;
    CityOfWinnipegService cityOfWinnipegService;


    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @PostConstruct
    public void initialize() throws SQLException, UnirestException, JsonProcessingException {
        this.cityOfWinnipegSecret = env.getProperty("secret.cityOfWinnipeg");
        this.googleApiKey = env.getProperty("secret.googleMaps");
        this.cityOfWinnipegHost = env.getProperty("endpoint.cityOfWinnipeg.host");
        this.cityOfWinnipegPath = env.getProperty("endpoint.cityOfWinnipeg.path");
        this.cityOfWinnipegQuery = env.getProperty("endpoint.cityOfWinnipeg.query");
        this.cityOfWinnipegService = new CityOfWinnipegService(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
        this.database = new Database(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
    }
    @Scheduled(cron = "0 */10 * * * ?")
    public void reportCurrentTime() throws SQLException, UnirestException, JsonProcessingException {
        database.syncIncidentsTable();
    }
}
