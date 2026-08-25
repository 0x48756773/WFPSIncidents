package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class AppController {

    private final Database database;

    public AppController(Database database) {
        this.database = database;
    }

    @GetMapping(value = "/")
    public String getIncidents(Model model) {
        List<Map<String, Object>> incidentList = database.getRecentIncidents();

        // An empty table means the last sync failed, so try once to recover. Rate-limited
        // inside the repository: during an outage this must not make every page load block
        // on its own failing upstream call.
        if (incidentList.isEmpty()) {
            database.tryRecoverySync();
            incidentList = database.getRecentIncidents();
        }

        List<String> neighbourhoodList = new ArrayList<>();
        for (Map<String, Object> incident : incidentList) {
            neighbourhoodList.add((String) incident.get("NEIGHBOURHOOD"));
        }

        model.addAttribute("incidents", incidentList);
        model.addAttribute("neighbourhoodList", neighbourhoodList);
        model.addAttribute("dataSourceAvailable", database.isDataSourceAvailable());
        return "index";
    }
}
