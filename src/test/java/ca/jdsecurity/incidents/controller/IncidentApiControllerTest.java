package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.database.Database;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * This endpoint is what an open page polls instead of reloading itself. It has to carry
 * everything the page needs to patch itself — the calls, the answer sentence, the freshness
 * line and whether the upstream source is reachable — because anything missing here is
 * something that silently stops updating until the reader navigates.
 */
@WebMvcTest(IncidentApiController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author"
})
class IncidentApiControllerTest {

    private static final ZonedDateTime SYNCED_AT =
            ZonedDateTime.of(2026, 9, 18, 9, 30, 0, 0, ZoneId.of("America/Winnipeg"));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private static Map<String, Object> incident(String number, String type, boolean closed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("INCIDENT_NUMBER", number);
        row.put("INCIDENT_TYPE", type);
        row.put("NEIGHBOURHOOD", "Wolseley");
        row.put("CLOSED", closed);
        return row;
    }

    @Test
    void servesTheIncidentListWithTheCountsThePageDisplays() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false),
                incident("26-002", "Medical Response - Fall", false),
                incident("26-003", "Fire Rescue - Grass", true)));
        when(database.isDataSourceAvailable()).thenReturn(true);
        when(database.getLastSuccessfulSync()).thenReturn(SYNCED_AT);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents.length()").value(3))
                .andExpect(jsonPath("$.incidents[0].INCIDENT_NUMBER").value("26-001"))
                .andExpect(jsonPath("$.summary.activeFire").value(1))
                .andExpect(jsonPath("$.summary.activeMedical").value(1))
                .andExpect(jsonPath("$.summary.activeTotal").value(2))
                .andExpect(jsonPath("$.summary.closed").value(1))
                .andExpect(jsonPath("$.dataSourceAvailable").value(true))
                .andExpect(jsonPath("$.lastUpdatedIso").value("2026-09-18T09:30:00-05:00"));
    }

    /**
     * The sentence is written server-side so the page and the poll cannot end up wording the
     * same fact differently. If it stopped being sent, the summary would freeze at whatever
     * the page was rendered with and quietly go stale.
     */
    @Test
    void sendsTheAnswerSentenceReadyToDisplay() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false)));
        when(database.isDataSourceAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(jsonPath("$.summarySentence")
                        .value("1 Winnipeg Fire Paramedic Service call is active right now — 1 fire rescue."));
    }

    /** A poll must still see the source going down, even when the data behind it has not moved. */
    @Test
    void reportsTheSourceBeingUnreachable() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false)));
        when(database.isDataSourceAvailable()).thenReturn(false);
        when(database.getLastSuccessfulSync()).thenReturn(SYNCED_AT);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(jsonPath("$.dataSourceAvailable").value(false))
                .andExpect(header().string("ETag", "\"" + SYNCED_AT.toEpochSecond() + "-down\""));
    }

    @Test
    void tagsTheResponseWithTheSyncTimeSoAnUnchangedPollIsCheap() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false)));
        when(database.isDataSourceAvailable()).thenReturn(true);
        when(database.getLastSuccessfulSync()).thenReturn(SYNCED_AT);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(header().string("ETag", "\"" + SYNCED_AT.toEpochSecond() + "-up\""));
    }

    /** Never cached: the whole point of the poll is to see something that has changed. */
    @Test
    void forbidsCachingTheResponse() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of());
        when(database.isDataSourceAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
    }

    /** An empty table means the last sync failed, exactly as on the page. */
    @Test
    void attemptsRecoveryWhenTheTableIsEmpty() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of());
        when(database.isDataSourceAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/incidents")).andExpect(status().isOk());

        verify(database).tryRecoverySync();
    }

    @Test
    void doesNotAttemptRecoveryWhenThereIsData() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false)));
        when(database.isDataSourceAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/incidents")).andExpect(status().isOk());

        verify(database, never()).tryRecoverySync();
    }

    /** Before the first successful sync there is no honest timestamp, so none is sent. */
    @Test
    void omitsTheTimestampBeforeAnySuccessfulSync() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false)));
        when(database.isDataSourceAvailable()).thenReturn(true);
        when(database.getLastSuccessfulSync()).thenReturn(null);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(jsonPath("$.lastUpdatedIso").doesNotExist())
                .andExpect(jsonPath("$.lastUpdatedDisplay").doesNotExist())
                .andExpect(header().doesNotExist("ETag"));
    }
}
