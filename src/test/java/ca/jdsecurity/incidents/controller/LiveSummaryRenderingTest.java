package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.incident.IncidentCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The single most-searched thing this page answers is a factual question — is there a fire
 * in Winnipeg right now — and the answer used to be available only by reading the map. It is
 * now a sentence, and it has to be rendered into the HTML rather than painted in afterwards:
 * a crawler does not wait for the map to draw, and neither does a reader on a slow phone.
 */
@WebMvcTest(AppController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author"
})
class LiveSummaryRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    /**
     * Every column the table template reads has to be present: Thymeleaf resolves these
     * against the map by key, and a key that is simply absent is an error rather than a
     * blank cell.
     */
    private static Map<String, Object> incident(String number, String type, boolean closed) {
        IncidentCategory category = IncidentCategory.of(type);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("INCIDENT_NUMBER", number);
        row.put("INCIDENT_TYPE", type);
        row.put("CATEGORY", category.getId());
        row.put("CATEGORY_LABEL", category.getLabel());
        row.put("IS_MOTOR", "No");
        row.put("UNITS", "E1, L2");
        row.put("NEIGHBOURHOOD", "Wolseley");
        row.put("WARD", "Daniel McIntyre");
        row.put("CALL_TIME", "September 18, 2026 at 09:15");
        row.put("CLOSED", closed);
        row.put("CLOSED_TIME", closed ? "September 18, 2026 at 09:45" : "");
        row.put("DURATION", closed ? "30m" : "");
        return row;
    }

    private String render() throws Exception {
        return mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theQuestionIsAskedOnThePage() throws Exception {
        assertThat(render()).contains("Is there a fire in Winnipeg right now?");
    }

    @Test
    void theAnswerIsInTheHtmlWithRealCounts() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", false),
                incident("26-002", "Medical Response - Fall", false),
                incident("26-003", "Medical Response - Cardiac", false),
                incident("26-004", "Fire Rescue - Grass", true)));

        String html = render();

        assertThat(html).contains("3 Winnipeg Fire Paramedic Service calls are active right now");
        assertThat(html).contains("1 fire rescue call");
        assertThat(html).contains("2 medical responses");
    }

    /** Closed calls are still listed on the page, but they are not what "right now" means. */
    @Test
    void closedCallsDoNotCountTowardsTheLiveTotal() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("26-001", "Fire Rescue - Structure", true),
                incident("26-002", "Medical Response - Fall", true)));

        assertThat(render()).contains("No Winnipeg Fire Paramedic Service calls are active right now.");
    }

    @Test
    void aQuietPeriodIsStatedPlainlyRatherThanLeftBlank() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of());

        assertThat(render()).contains("No Winnipeg Fire Paramedic Service calls are active right now.");
    }
}
