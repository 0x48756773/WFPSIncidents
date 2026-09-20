package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.model.IncidentSummary;
import org.springframework.beans.factory.annotation.Value;
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

    /** Shared with {@link IncidentApiController}, so the page and the poll format one time one way. */
    static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm z");

    // Carries the service's full name *and* its acronym, because search treats them as
    // different queries and the site was doing well on only one of them: it ranked around
    // position 2 for "wfps ..." phrasings while sitting on page two for "winnipeg fire
    // paramedic service", which drew impressions and almost no clicks. Both now appear
    // verbatim, inside the ~60 characters Google will actually display.
    private static final String PAGE_TITLE =
            "Winnipeg Fire Paramedic Service (WFPS) Live Incident Map";
    // Opens with the question the largest group of visitors actually typed. Search bolds the
    // words a query matched, so leading with the phrasing people use is worth more here than
    // leading with a description of the software.
    private static final String PAGE_DESCRIPTION_TEMPLATE =
            "Where is the fire in Winnipeg right now? Live WFPS map of active fire, medical and "
                    + "rescue calls, updated every %s from City of Winnipeg open data.";

    private final Database database;
    private final RefreshCadence refreshCadence;
    private final String mapTilesKey;

    public AppController(Database database, RefreshCadence refreshCadence,
                         @Value("${app.mapTilesKey:}") String mapTilesKey) {
        this.database = database;
        this.refreshCadence = refreshCadence;
        this.mapTilesKey = mapTilesKey;
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
        // The answer to "is there a fire in Winnipeg right now", rendered into the HTML
        // rather than painted in by the map script, so it is readable without JavaScript
        // and present for a crawler that does not wait for one.
        model.addAttribute("summary", IncidentSummary.of(incidentList));
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
        // CARTO now requires a key on its basemaps, and only this page draws a map. Handed
        // to the browser from configuration so the cached maps.js does not have to be
        // re-fetched -- or wait out its 30-day cache -- when the key is rotated.
        model.addAttribute("mapTilesKey", mapTilesKey);
        return "index";
    }
}
