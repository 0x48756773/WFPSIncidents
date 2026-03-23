package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Controller
public class AppController {
    Database database;
    ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(AppController.class);
    @Autowired
    private Environment env;
    String cityOfWinnipegSecret;
    String cityOfWinnipegHost;
    String cityOfWinnipegPath;
    String cityOfWinnipegQuery;
    CityOfWinnipegService cityOfWinnipegService;


    @PostConstruct
    public void initialize() throws SQLException {
        this.cityOfWinnipegSecret = env.getProperty("secret.cityOfWinnipeg");
        this.cityOfWinnipegHost = env.getProperty("endpoint.cityOfWinnipeg.host");
        this.cityOfWinnipegPath = env.getProperty("endpoint.cityOfWinnipeg.path");
        this.cityOfWinnipegQuery = env.getProperty("endpoint.cityOfWinnipeg.query");
        this.cityOfWinnipegService = new CityOfWinnipegService(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
        this.database = new Database(cityOfWinnipegSecret, cityOfWinnipegHost, cityOfWinnipegPath, cityOfWinnipegQuery);
        database.syncIncidentsTableSafe();
    }

    @GetMapping(value = "/")
    public String getTestData(Model model) throws SQLException {
        List<HashMap<String, Object>> incidentList = database.getAllIncidentsFromToday();

        // Fallback in case a race condition occurs where the table is not populated prior to the scheduled task.
        if (incidentList.isEmpty()) {
            database.syncIncidentsTableSafe();
            incidentList = database.getAllIncidentsFromToday();
        }

        List<String> neighbourhoodList = new ArrayList<>();
        for (HashMap<String, Object> incident : incidentList) {
            neighbourhoodList.add((String) incident.get("NEIGHBOURHOOD"));
        }

        model.addAttribute("incidents", incidentList);
        model.addAttribute("neighbourhoodList", neighbourhoodList);
        model.addAttribute("dataSourceAvailable", Database.isDataSourceAvailable());
        return "index";
    }
}
