package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.database.Database;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.WebRequest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class AppController {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm z");

    // Leads with the phrase the page competes for, while keeping WFPS — the acronym the site
    // already ranks for. The full service name is carried by the description, the subtitle and
    // the structured data, so dropping it here does not remove it from the page.
    private static final String PAGE_TITLE =
            "Winnipeg Fire Incidents – Live WFPS Active Incident Map";
    private static final String PAGE_DESCRIPTION_TEMPLATE =
            "Live map of Winnipeg fire incidents, medical responses and rescue calls, updated every "
                    + "%s from Winnipeg Fire Paramedic Service dispatch data.";

    private final Database database;
    private final RefreshCadence refreshCadence;

    public AppController(Database database, RefreshCadence refreshCadence) {
        this.database = database;
        this.refreshCadence = refreshCadence;
    }

    @GetMapping(value = "/")
    public String getIncidents(Model model, WebRequest webRequest) {
        List<Map<String, Object>> incidentList = database.getRecentIncidents();

        // An empty table means the last sync failed, so try once to recover. Rate-limited
        // inside the repository: during an outage this must not make every page load block
        // on its own failing upstream call.
        if (incidentList.isEmpty()) {
            database.tryRecoverySync();
            incidentList = database.getRecentIncidents();
        }

        // Conditional GET. The response is a function of the last successful sync and
        // whether the source is reachable, so both go in the tag — keyed on the sync alone,
        // a failed refresh would 304 away the "data source unavailable" banner.
        ZonedDateTime lastSync = database.getLastSuccessfulSync();
        boolean available = database.isDataSourceAvailable();
        if (lastSync != null) {
            String etag = "\"" + lastSync.toEpochSecond() + "-" + (available ? "up" : "down") + "\"";
            if (webRequest.checkNotModified(etag)) {
                return null;
            }
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
        model.addAttribute("lastUpdatedIso",
                lastSync == null ? null : lastSync.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        model.addAttribute("lastUpdatedDisplay",
                lastSync == null ? null : lastSync.format(DISPLAY));
        // One source for the title and description: they appear three times each in the head
        // (plain, Open Graph, Twitter) and drift between copies otherwise.
        model.addAttribute("pageTitle", PAGE_TITLE);
        model.addAttribute("pageDescription",
                PAGE_DESCRIPTION_TEMPLATE.formatted(refreshCadence.getLabel()));
        model.addAttribute("dataSourceAvailable", available);
        return "index";
    }
}
