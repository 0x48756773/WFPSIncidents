package ca.jdsecurity.incidents.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The summary is the page's answer to the question most of its visitors typed into a search
 * engine, so it has to be right about the count and readable as a sentence.
 */
class IncidentSummaryTest {

    private static Map<String, Object> incident(String type, boolean closed) {
        return Map.of("INCIDENT_TYPE", type, "CLOSED", closed);
    }

    @Test
    void countsActiveCallsByCategoryAndIgnoresClosedOnes() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                incident("Fire Rescue - Structure", false),
                incident("Fire Rescue - Vehicle", false),
                incident("Medical Response - Cardiac", false),
                incident("Alarm Bells Ringing", false),
                incident("Fire Rescue - Grass", true)));

        assertThat(summary.activeFire()).isEqualTo(2);
        assertThat(summary.activeMedical()).isEqualTo(1);
        assertThat(summary.activeOther()).isEqualTo(1);
        assertThat(summary.activeTotal()).isEqualTo(4);
        assertThat(summary.closed()).isEqualTo(1);
    }

    /** The grouping has to agree with the table template and the map markers. */
    @Test
    void categorisesTheSameWayTheTableAndTheMapDo() {
        assertThat(IncidentSummary.categoryOf("Fire Rescue - Alarms")).isEqualTo("fire");
        assertThat(IncidentSummary.categoryOf("fire rescue - lowercase")).isEqualTo("fire");
        assertThat(IncidentSummary.categoryOf("Medical Response - Fall")).isEqualTo("medical");
        assertThat(IncidentSummary.categoryOf("Motor Vehicle Collision")).isEqualTo("other");
        assertThat(IncidentSummary.categoryOf(null)).isEqualTo("other");
    }

    /** A type naming a fire wins over one merely containing "medical". */
    @Test
    void fireTakesPrecedenceOverMedical() {
        assertThat(IncidentSummary.categoryOf("Fire Rescue - Medical Response Assist")).isEqualTo("fire");
    }

    /**
     * The sentence is the page's answer to "is there a fire in Winnipeg right now?", so it
     * has to count the same calls the table badges as fires -- "Fire Response" included.
     */
    @Test
    void countsEveryFireTheTableShowsAsAFire() {
        assertThat(IncidentSummary.categoryOf("Fire Response")).isEqualTo("fire");
        assertThat(IncidentSummary.categoryOf("Alarm - No Fire")).isEqualTo("other");

        IncidentSummary summary = IncidentSummary.of(List.of(
                incident("Fire Response", false),
                incident("Alarm - No Fire", false)));

        assertThat(summary.activeFire()).isEqualTo(1);
        assertThat(summary.activeOther()).isEqualTo(1);
    }

    @Test
    void readsAsASentenceWhenNothingIsActive() {
        assertThat(IncidentSummary.of(List.of()).sentence())
                .isEqualTo("No Winnipeg Fire Paramedic Service calls are active right now.");
    }

    @Test
    void readsAsASentenceForASingleCall() {
        String sentence = IncidentSummary.of(List.of(incident("Fire Rescue - Structure", false))).sentence();

        assertThat(sentence).isEqualTo(
                "1 Winnipeg Fire Paramedic Service call is active right now — 1 fire rescue call.");
    }

    @Test
    void readsAsASentenceForSeveralCategories() {
        String sentence = IncidentSummary.of(List.of(
                incident("Fire Rescue - Structure", false),
                incident("Medical Response - Cardiac", false),
                incident("Medical Response - Fall", false),
                incident("Alarm Bells Ringing", false))).sentence();

        assertThat(sentence).isEqualTo("4 Winnipeg Fire Paramedic Service calls are active right now"
                + " — 1 fire rescue call, 2 medical responses and 1 other call.");
    }

    /** Only the categories that actually have calls are named. */
    @Test
    void omitsEmptyCategories() {
        String sentence = IncidentSummary.of(List.of(
                incident("Medical Response - Cardiac", false),
                incident("Medical Response - Fall", false))).sentence();

        assertThat(sentence).doesNotContain("fire rescue").doesNotContain("other");
        assertThat(sentence).contains("2 medical responses");
    }

    @Test
    void toleratesAMissingList() {
        assertThat(IncidentSummary.of(null).activeTotal()).isZero();
    }
}
