package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class AppController {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm z");

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
        // Freshness the crawler can read. The countdown badge in the table legend is
        // client-rendered and points at the *next* refresh; this is the last completed one.
        // Left null before the first successful sync so the page cannot claim a stale time.
        ZonedDateTime lastSync = database.getLastSuccessfulSync();
        model.addAttribute("lastUpdatedIso",
                lastSync == null ? null : lastSync.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        model.addAttribute("lastUpdatedDisplay",
                lastSync == null ? null : lastSync.format(DISPLAY));
        model.addAttribute("dataSourceAvailable", database.isDataSourceAvailable());
        return "index";
    }
}
