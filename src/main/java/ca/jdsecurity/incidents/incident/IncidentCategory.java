package ca.jdsecurity.incidents.incident;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The three colour groups the map, the filter chips and the table badge sort incidents into.
 *
 * <p>WFPS publishes a free-text incident type, so this grouping is editorial — a readability
 * aid, not an official classification. The exact published type is always shown alongside it.
 *
 * <p>The rule used to be written out twice, once in Thymeleaf and once in {@code maps.js}, and
 * matched only types <em>beginning</em> "Fire Rescue". That missed every other way the feed
 * names a fire — "Fire Response" being the common one — and dropped those calls into Other.
 * Matching the word "fire" anywhere in the type covers them, and computing it here means the
 * table and the map cannot drift into disagreeing about what counts as a fire.
 */
public enum IncidentCategory {

    FIRE("fire", "Fire Rescue"),
    MEDICAL("medical", "Medical Response"),
    OTHER("other", "Other");

    /** Whole-word, so a "Firearm" call is not a fire. */
    private static final Pattern FIRE_WORD = Pattern.compile("\\bfire\\b");

    /**
     * Types that report the <em>absence</em> of a fire — "Alarm - No Fire", "Non-Fire Rescue".
     * The word is there, but the call is not a fire, so the negation is checked first and wins.
     */
    private static final Pattern NO_FIRE = Pattern.compile("\\b(?:no|non)[\\s-]+fire\\b");

    private final String id;
    private final String label;

    IncidentCategory(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** Lower-case form used in CSS class names, the filter chips' {@code data-category} and maps.js. */
    public String getId() {
        return id;
    }

    /** Human-readable group name shown on the badge. */
    public String getLabel() {
        return label;
    }

    /** Fire is tested first, so a type naming both stays with the fire response. */
    public static IncidentCategory of(String incidentType) {
        String normalized = incidentType == null ? "" : incidentType.toLowerCase(Locale.ROOT);
        if (isFire(normalized)) {
            return FIRE;
        }
        if (normalized.contains("medical response")) {
            return MEDICAL;
        }
        return OTHER;
    }

    private static boolean isFire(String normalized) {
        // A type saying there was no fire is not a fire call, whatever else it names: the
        // crew attended an alarm. Negation is checked over the whole type, so "Fire Alarm -
        // No Fire" is grouped by its outcome rather than by the words it opens with.
        if (NO_FIRE.matcher(normalized).find()) {
            return false;
        }
        return FIRE_WORD.matcher(normalized).find();
    }
}
