package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.model.IncidentSummary;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The incident list as JSON, so the open page can refresh itself without reloading.
 *
 * <p>The page used to call {@code location.reload()} on a timer. That threw away everything
 * the reader had done — the map's pan and zoom, their scroll position, an open popup, the
 * row they had selected — once a minute, which for a map you are meant to watch is the
 * whole interaction. It also fired a fresh {@code page_view} every minute, so the analytics
 * counted a parked tab as dozens of visits.
 *
 * <p>Serving the same data the page was built from lets the client patch itself in place.
 * The first render stays server-side, so crawlers and no-JS readers are unaffected.
 */
@RestController
public class IncidentApiController {

    private final Database database;

    public IncidentApiController(Database database) {
        this.database = database;
    }

    @GetMapping(value = "/api/incidents", produces = "application/json")
    public ResponseEntity<Map<String, Object>> incidents() {
        List<Map<String, Object>> incidentList = database.getRecentIncidents();

        // Same recovery attempt the page makes: an empty table means the last sync failed.
        // Rate-limited inside the repository, so a poll storm during an upstream outage
        // cannot turn into an upstream request storm.
        if (incidentList.isEmpty()) {
            database.tryRecoverySync();
            incidentList = database.getRecentIncidents();
        }

        ZonedDateTime lastSync = database.getLastSuccessfulSync();
        boolean available = database.isDataSourceAvailable();

        IncidentSummary summary = IncidentSummary.of(incidentList);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("incidents", incidentList);
        body.put("summary", summary);
        // Sent already written rather than left for the client to assemble: the sentence has
        // number agreement in it, and a second copy of that in JavaScript is a second copy
        // to keep in step with this one.
        body.put("summarySentence", summary.sentence());
        // Omitted rather than sent as null before the first successful sync, for the same
        // reason the sitemap omits lastmod: there is no honest value, and the client
        // treating a null as "no change" is one fewer thing to get wrong.
        if (lastSync != null) {
            body.put("lastUpdatedIso", lastSync.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            body.put("lastUpdatedDisplay", lastSync.format(AppController.DISPLAY));
        }
        body.put("dataSourceAvailable", available);

        // Keyed the same way as the page's own ETag — on the sync time and on whether the
        // source is reachable, because a poll must still see the source going down even
        // when the data behind it has not changed.
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate());
        if (lastSync != null) {
            response.eTag("\"" + lastSync.toEpochSecond() + "-" + (available ? "up" : "down") + "\"");
        }
        return response.body(body);
    }
}
