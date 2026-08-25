package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Atom feed of recent incidents.
 *
 * <p>Deliberately a feed rather than automated posting to a social account. A feed is pulled
 * by someone who chose to subscribe; it does not broadcast live emergencies at an audience
 * that did not ask, which is the objection to pushing individual incidents outward.
 *
 * <p>Atom rather than RSS because an incident changes after publication — it closes — and
 * Atom distinguishes {@code published} from {@code updated}, so a reader can tell a new call
 * from one that has just ended. RSS has no equivalent.
 */
@Controller
public class FeedController {

    /** Feed readers want the recent past, not the whole retention window. */
    private static final int MAX_ENTRIES = 50;

    private final Database database;
    private final String baseUrl;
    private final String authorName;

    public FeedController(
            Database database,
            @Value("${app.baseUrl}") String baseUrl,
            @Value("${app.authorName}") String authorName) {
        this.database = database;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authorName = authorName;
    }

    @GetMapping(value = "/feed.xml", produces = "application/atom+xml;charset=UTF-8")
    @ResponseBody
    public String feed() {
        List<Map<String, Object>> incidents = database.getRecentIncidents();
        ZonedDateTime lastSync = database.getLastSuccessfulSync();

        StringBuilder xml = new StringBuilder();
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>WFPS Active Incident Map</title>
                  <subtitle>Recent Winnipeg Fire Paramedic Service calls, from the City of Winnipeg's published open data. Not an emergency service — call 911 in an emergency. Locations are neighbourhood-level only.</subtitle>
                  <link rel="self" type="application/atom+xml" href="%s/feed.xml"/>
                  <link rel="alternate" type="text/html" href="%s/"/>
                  <id>%s/</id>
                  <author><name>%s</name></author>
                  <rights>Incident data published by the City of Winnipeg. This feed is an unofficial view of it.</rights>
                """.formatted(baseUrl, baseUrl, baseUrl, escape(authorName)));

        // Feed-level updated: when the data was last refreshed, not when this was requested —
        // a timestamp that moves on every fetch tells a reader nothing about whether anything changed.
        if (lastSync != null) {
            xml.append("  <updated>")
               .append(lastSync.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
               .append("</updated>\n");
        }

        incidents.stream().limit(MAX_ENTRIES).forEach(incident -> appendEntry(xml, incident));

        return xml.append("</feed>\n").toString();
    }

    private void appendEntry(StringBuilder xml, Map<String, Object> incident) {
        String number = string(incident.get("INCIDENT_NUMBER"));
        String type = string(incident.get("INCIDENT_TYPE"));
        String neighbourhood = string(incident.get("NEIGHBOURHOOD"));
        String callTimeIso = string(incident.get("CALL_TIME_ISO"));
        String closedTimeIso = string(incident.get("CLOSED_TIME_ISO"));
        boolean closed = Boolean.TRUE.equals(incident.get("CLOSED"));

        // An entry must carry a timestamp; without a parseable call time there is nothing to date it by.
        if (callTimeIso.isEmpty()) {
            return;
        }
        String updated = closed && !closedTimeIso.isEmpty() ? closedTimeIso : callTimeIso;

        StringBuilder summary = new StringBuilder();
        summary.append(closed ? "Closed" : "Active").append(" — ").append(type);
        if (!neighbourhood.isEmpty()) {
            summary.append(" in ").append(neighbourhood);
        }
        appendIfPresent(summary, "Units dispatched", incident.get("UNITS"));
        appendIfPresent(summary, "Ward", incident.get("WARD"));
        appendIfPresent(summary, "Called", incident.get("CALL_TIME"));
        if (closed) {
            appendIfPresent(summary, "On scene", incident.get("DURATION"));
        }
        summary.append(". Location is neighbourhood-level only; early records may be revised.");

        xml.append("  <entry>\n")
           .append("    <title>").append(escape(entryTitle(type, neighbourhood, closed))).append("</title>\n")
           // Incident numbers are stable, so a reader that has seen an entry recognises it again
           // when the call later closes, and updates it rather than showing it twice.
           .append("    <id>urn:wfps:incident:").append(escape(number)).append("</id>\n")
           .append("    <published>").append(escape(callTimeIso)).append("</published>\n")
           .append("    <updated>").append(escape(updated)).append("</updated>\n")
           .append("    <link rel=\"alternate\" type=\"text/html\" href=\"").append(baseUrl).append("/\"/>\n")
           .append("    <summary type=\"text\">").append(escape(summary.toString())).append("</summary>\n")
           .append("  </entry>\n");
    }

    private String entryTitle(String type, String neighbourhood, boolean closed) {
        String title = type.isEmpty() ? "Incident" : type;
        if (!neighbourhood.isEmpty()) {
            title = title + " — " + neighbourhood;
        }
        return closed ? title + " (closed)" : title;
    }

    private void appendIfPresent(StringBuilder summary, String label, Object value) {
        String text = string(value);
        if (!text.isEmpty()) {
            summary.append(". ").append(label).append(": ").append(text);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * Incident type and neighbourhood come from an external feed, so nothing reaches the
     * document unescaped — one stray ampersand would otherwise make the whole feed unparseable.
     */
    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
