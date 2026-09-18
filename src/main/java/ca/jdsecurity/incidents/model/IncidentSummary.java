package ca.jdsecurity.incidents.model;

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
 * <p>The category rules here must agree with the ones in the incident table template and
 * in {@code maps.js}. All three classify the same free-text field, so a change to one is
 * a change to all three; {@link #categoryOf(String)} is the copy this class owns.
 */
public record IncidentSummary(
        int activeFire,
        int activeMedical,
        int activeOther,
        int activeTotal,
        int closed) {

    public static IncidentSummary of(List<Map<String, Object>> incidents) {
        int fire = 0, medical = 0, other = 0, closedCount = 0;

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
        }

        return new IncidentSummary(fire, medical, other, fire + medical + other, closedCount);
    }

    /** Mirrors the grouping used by the table template and the map markers. */
    public static String categoryOf(String incidentType) {
        String normalized = incidentType == null ? "" : incidentType.toLowerCase();
        if (normalized.startsWith("fire rescue")) {
            return "fire";
        }
        if (normalized.contains("medical response")) {
            return "medical";
        }
        return "other";
    }

    private static boolean isClosed(Map<String, Object> incident) {
        Object closed = incident.get("CLOSED");
        return Boolean.TRUE.equals(closed) || "true".equals(String.valueOf(closed));
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
            parts.add(activeFire + " fire rescue");
        }
        if (activeMedical > 0) {
            parts.add(activeMedical + " medical response");
        }
        if (activeOther > 0) {
            parts.add(activeOther + " other");
        }

        return "%d Winnipeg Fire Paramedic Service %s active right now — %s."
                .formatted(activeTotal, activeTotal == 1 ? "call is" : "calls are", join(parts));
    }

    private static String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String last = parts.get(parts.size() - 1);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + last;
    }
}
