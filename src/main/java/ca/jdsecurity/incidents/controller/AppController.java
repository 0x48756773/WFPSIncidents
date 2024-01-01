package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.exceptions.UnirestException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Controller
public class AppController {
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

    @PostConstruct
    public void initialize() throws SQLException, UnirestException, JsonProcessingException {
        this.cityOfWinnipegSecret = env.getProperty("secret.cityOfWinnipeg");
        this.googleApiKey = env.getProperty("secret.googleMaps");
        this.cityOfWinnipegHost = env.getProperty("endpoint.cityOfWinnipeg.host");
        this.cityOfWinnipegPath = env.getProperty("endpoint.cityOfWinnipeg.path");
        this.cityOfWinnipegQuery = env.getProperty("endpoint.cityOfWinnipeg.query");
        this.cityOfWinnipegService = new CityOfWinnipegService(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
        this.database = new Database(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
        database.syncIncidentsTable();
    }
    @GetMapping(value = "/")
    public String getTestData(Model model) throws UnirestException, IOException, SQLException {
        List<HashMap<String, Object>> incidentList = database.getAllIncidentsFromToday();

        // This is a fallback in case a race condition occurs where the table is not populated prior to the scheduled task.
        if (incidentList.size()==0){
            database.syncIncidentsTable();
        }
        List<String> neighbourhoodList = new ArrayList<>();
        for (int i = 0; i < incidentList.size(); i++) {
            HashMap<String,Object> incident = incidentList.get(i);
            neighbourhoodList.add((String) incident.get("NEIGHBOURHOOD"));
        }

        model.addAttribute("incidents", incidentList);
        model.addAttribute("neighbourhoodList", neighbourhoodList);
        return "index";
    }
}
