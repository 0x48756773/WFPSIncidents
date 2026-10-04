package ca.jdsecurity.incidents.model;

import ca.jdsecurity.incidents.incident.IncidentCategory;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Counts of what is happening right now, derived from the incident list.
 *
 * <p>This exists because the page's single most-searched question is a factual one —
 * "is there a fire in Winnipeg right now?" — and the answer was previously only visible
 * by reading the map. Rendering it as a sentence server-side means the answer is in the
 * HTML a crawler sees, not painted in afterwards by JavaScript.
 *
 * <p>The counts are grouped by {@link IncidentCategory}, the same rule the table badge and
 * the map markers use. This class held its own copy of it, which is how the sentence could
 * have ended up reporting no fires under a table showing them.
 *
 * <p>{@code oldestAwaitingDispatchMinutes} is {@code null} rather than zero when nothing is
 * waiting — or when the waiting calls carry no parseable call time — for the same reason the
 * sitemap omits {@code lastmod} before the first sync: zero minutes is a real value, and
 * sending it for "unknown" invites the client to print "waiting 0m" about a call it cannot
 * time.
 */
public record IncidentSummary(
        int activeFire,
        int activeMedical,
        int activeOther,
        int activeTotal,
        int closed,
        int awaitingDispatch,
        Long oldestAwaitingDispatchMinutes) {

    public static IncidentSummary of(List<Map<String, Object>> incidents) {
        return of(incidents, Instant.now());
    }

    /** Takes the clock as an argument so the waiting time is a fixed value under test. */
    public static IncidentSummary of(List<Map<String, Object>> incidents, Instant now) {
        int fire = 0, medical = 0, other = 0, closedCount = 0, awaiting = 0;
        Instant oldestAwaitingCall = null;

        for (Map<String, Object> incident : incidents == null ? List.<Map<String, Object>>of() : incidents) {
            if (isClosed(incident)) {
                closedCount++;
                continue;
            }
            switch (categoryOf((String) incident.get("INCIDENT_TYPE"))) {
                case "fire" -> fire++;
                case "medical" -> medical++;
                default -> other++;
            }
            if (isAwaitingDispatch(incident)) {
                awaiting++;
                Instant calledAt = callTimeOf(incident);
                if (calledAt != null && (oldestAwaitingCall == null || calledAt.isBefore(oldestAwaitingCall))) {
                    oldestAwaitingCall = calledAt;
                }
            }
        }

        return new IncidentSummary(fire, medical, other, fire + medical + other, closedCount,
                awaiting, minutesWaiting(oldestAwaitingCall, now));
    }

    /** The grouping used by the table template and the map markers, not a second copy of it. */
    public static String categoryOf(String incidentType) {
        return IncidentCategory.of(incidentType).getId();
    }

    private static boolean isClosed(Map<String, Object> incident) {
        Object closed = incident.get("CLOSED");
        return Boolean.TRUE.equals(closed) || "true".equals(String.valueOf(closed));
    }

    /**
     * An active call with nothing assigned to it yet. The feed omits {@code units} entirely
     * until a unit is attached to the incident, so an empty value on a live record is the
     * only signal available that a call has been published but not yet dispatched.
     *
     * <p>Closed calls are excluded rather than counted as waiting forever: an empty units
     * field on a record that has already closed describes a call no unit ever attended
     * (which is why the popup labels it "No Response"), not one still waiting for one.
     */
    public static boolean isAwaitingDispatch(Map<String, Object> incident) {
        if (incident == null || isClosed(incident)) {
            return false;
        }
        Object units = incident.get("UNITS");
        return units == null || String.valueOf(units).isBlank();
    }

    /**
     * The answer, as a sentence. Written here rather than assembled in the template because
     * it needs number agreement, and Thymeleaf conditionals for that are harder to read than
     * the string they produce.
     */
    public String sentence() {
        if (activeTotal == 0) {
            return "No Winnipeg Fire Paramedic Service calls are active right now.";
        }

        List<String> parts = new ArrayList<>();
        if (activeFire > 0) {
            parts.add(count(activeFire, "fire rescue call", "fire rescue calls"));
        }
        if (activeMedical > 0) {
            parts.add(count(activeMedical, "medical response", "medical responses"));
        }
        if (activeOther > 0) {
            parts.add(count(activeOther, "other call", "other calls"));
        }

        return "%d Winnipeg Fire Paramedic Service %s active right now — %s."
                .formatted(activeTotal, activeTotal == 1 ? "call is" : "calls are", join(parts));
    }

    /**
     * How many calls are published but not yet dispatched, and how long the one that has
     * been waiting longest has waited. Empty when nothing is waiting — the page hides the
     * line rather than printing "0 calls", since the interesting state is the exception.
     *
     * <p>A second sentence rather than a clause inside {@link #sentence()}: that one answers
     * the question in the heading above it, and it is the text search engines read as the
     * page's answer.
     */
    public String dispatchSentence() {
        if (awaitingDispatch == 0) {
            return "";
        }

        String calls = awaitingDispatch == 1 ? "1 active call is" : awaitingDispatch + " active calls are";
        if (oldestAwaitingDispatchMinutes == null) {
            return calls + " awaiting dispatch.";
        }

        String waited = waitLabel(oldestAwaitingDispatchMinutes);
        return awaitingDispatch == 1
                ? calls + " awaiting dispatch — waiting " + waited + " so far."
                : calls + " awaiting dispatch — the oldest has been waiting " + waited + ".";
    }

    /**
     * Each category pluralises differently: "medical response" takes an -s on the noun,
     * "fire rescue" needs the word "call" to take one at all, and "3 others" would read as
     * a different thing entirely. So each carries its own pair rather than sharing a rule.
     */
    private static String count(int number, String singular, String plural) {
        return number + " " + (number == 1 ? singular : plural);
    }

    private static String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String last = parts.get(parts.size() - 1);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + last;
    }

    /**
     * Reads the machine-readable call time the repository attaches alongside the display
     * one. Returns {@code null} for a row that carries no parseable value, so an unusable
     * timestamp costs the waiting time rather than the whole summary.
     */
    private static Instant callTimeOf(Map<String, Object> incident) {
        Object callTime = incident.get("CALL_TIME_ISO");
        if (callTime == null || String.valueOf(callTime).isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(String.valueOf(callTime)).toInstant();
        } catch (DateTimeParseException unparseable) {
            return null;
        }
    }

    private static Long minutesWaiting(Instant calledAt, Instant now) {
        if (calledAt == null) {
            return null;
        }
        // Clamped at zero: a call time slightly ahead of this server's clock is a clock
        // difference, not a call that will be dispatched in the past.
        return Math.max(0, Duration.between(calledAt, now).toMinutes());
    }

    /**
     * Same shape as the on-scene duration in the table ("1h 5m", "12m"), so the two
     * durations on the page read as the same kind of measurement. Sub-minute waits are
     * worded rather than printed as "0m".
     */
    private static String waitLabel(long minutes) {
        if (minutes < 1) {
            return "under a minute";
        }
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return hours > 0 ? hours + "h " + remainder + "m" : remainder + "m";
    }
}
