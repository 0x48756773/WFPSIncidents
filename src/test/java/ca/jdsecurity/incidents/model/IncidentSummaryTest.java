package ca.jdsecurity.incidents.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The summary is the page's answer to the question most of its visitors typed into a search
 * engine, so it has to be right about the count and readable as a sentence.
 */
class IncidentSummaryTest {

    /** The clock the waiting times below are measured against, so they are fixed values. */
    private static final Instant NOW = Instant.parse("2026-09-18T15:30:00Z");

    private static Map<String, Object> incident(String type, boolean closed) {
        return Map.of("INCIDENT_TYPE", type, "CLOSED", closed, "UNITS", "E1, L2");
    }

    /**
     * A call with no units on it. Built as a HashMap rather than with Map.of because the
     * absent-units case is literally a null value, which Map.of rejects -- and it is the
     * case that matters: the feed omits the field until a unit is attached.
     */
    private static Map<String, Object> undispatched(String type, boolean closed, String callTimeIso) {
        Map<String, Object> row = new HashMap<>();
        row.put("INCIDENT_TYPE", type);
        row.put("CLOSED", closed);
        row.put("UNITS", null);
        row.put("CALL_TIME_ISO", callTimeIso);
        return row;
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

    /**
     * The count that answers "has anything been sitting unanswered". Dispatched calls and
     * closed ones are both excluded: an empty units field on a closed record is a call no
     * unit ever attended, not one still waiting for one.
     */
    @Test
    void countsActiveCallsWithNoUnitsOnThem() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Medical Response - Fall", false, "2026-09-18T10:20:00-05:00"),
                undispatched("Fire Response", false, "2026-09-18T10:15:00-05:00"),
                undispatched("Alarm Bells Ringing", true, "2026-09-18T09:00:00-05:00"),
                incident("Fire Rescue - Structure", false)), NOW);

        assertThat(summary.awaitingDispatch()).isEqualTo(2);
        // Still counted in their categories -- an undispatched call is an active call.
        assertThat(summary.activeTotal()).isEqualTo(3);
    }

    /** A blank units value is as empty as an absent one. */
    @Test
    void treatsABlankUnitsValueAsAwaitingDispatch() {
        Map<String, Object> row = new HashMap<>();
        row.put("INCIDENT_TYPE", "Medical Response - Fall");
        row.put("CLOSED", false);
        row.put("UNITS", "   ");

        assertThat(IncidentSummary.of(List.of(row), NOW).awaitingDispatch()).isEqualTo(1);
    }

    @Test
    void measuresTheWaitFromTheOldestUndispatchedCall() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Medical Response - Fall", false, "2026-09-18T10:20:00-05:00"),
                // 72 minutes before NOW, and the one the sentence has to report.
                undispatched("Fire Response", false, "2026-09-18T09:18:00-05:00")), NOW);

        assertThat(summary.oldestAwaitingDispatchMinutes()).isEqualTo(72);
        assertThat(summary.dispatchSentence())
                .isEqualTo("2 active calls are awaiting dispatch — the oldest has been waiting 1h 12m.");
    }

    @Test
    void readsAsASentenceForASingleUndispatchedCall() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Fire Response", false, "2026-09-18T10:16:00-05:00")), NOW);

        assertThat(summary.dispatchSentence())
                .isEqualTo("1 active call is awaiting dispatch — waiting 14m so far.");
    }

    /** Nothing waiting is not news, so the page gets nothing to show rather than a zero. */
    @Test
    void saysNothingWhenEverythingHasUnitsOnIt() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                incident("Fire Rescue - Structure", false),
                undispatched("Medical Response - Fall", true, "2026-09-18T09:00:00-05:00")), NOW);

        assertThat(summary.awaitingDispatch()).isZero();
        assertThat(summary.oldestAwaitingDispatchMinutes()).isNull();
        assertThat(summary.dispatchSentence()).isEmpty();
    }

    /** A call published this minute has waited no measurable time; "0m" would read as a bug. */
    @Test
    void wordsAWaitShorterThanAMinute() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Fire Response", false, "2026-09-18T10:29:30-05:00")), NOW);

        assertThat(summary.dispatchSentence())
                .isEqualTo("1 active call is awaiting dispatch — waiting under a minute so far.");
    }

    /**
     * A clock difference between this server and the feed must not produce a negative wait,
     * which would read as a call dispatched in the future.
     */
    @Test
    void clampsACallTimeAheadOfTheClock() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Fire Response", false, "2026-09-18T10:35:00-05:00")), NOW);

        assertThat(summary.oldestAwaitingDispatchMinutes()).isZero();
    }

    /**
     * An unusable timestamp costs the duration, not the count: the page can still say
     * something is waiting even when it cannot say for how long.
     */
    @Test
    void stillCountsACallWhoseTimeCannotBeRead() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Fire Response", false, ""),
                undispatched("Medical Response - Fall", false, "not a timestamp")), NOW);

        assertThat(summary.awaitingDispatch()).isEqualTo(2);
        assertThat(summary.oldestAwaitingDispatchMinutes()).isNull();
        assertThat(summary.dispatchSentence()).isEqualTo("2 active calls are awaiting dispatch.");
    }

    /** The waiting count is about calls, not about the sentence above it. */
    @Test
    void leavesTheFireAnswerAlone() {
        IncidentSummary summary = IncidentSummary.of(List.of(
                undispatched("Fire Response", false, "2026-09-18T10:20:00-05:00")), NOW);

        assertThat(summary.sentence()).isEqualTo(
                "1 Winnipeg Fire Paramedic Service call is active right now — 1 fire rescue call.");
    }

    @Test
    void toleratesAMissingList() {
        assertThat(IncidentSummary.of(null).activeTotal()).isZero();
        assertThat(IncidentSummary.of(null).awaitingDispatch()).isZero();
        assertThat(IncidentSummary.of(null).oldestAwaitingDispatchMinutes()).isNull();
    }
}
