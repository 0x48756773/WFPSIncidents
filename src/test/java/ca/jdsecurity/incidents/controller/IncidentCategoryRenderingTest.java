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
 * The category the server computed has to reach both surfaces it drives: the table badge
 * Thymeleaf renders, and the WFPS_DATA blob maps.js colours and filters the markers from.
 */
@WebMvcTest(AppController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author"
})
class IncidentCategoryRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private static Map<String, Object> incident(String number, String type) {
        IncidentCategory category = IncidentCategory.of(type);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("INCIDENT_NUMBER", number);
        row.put("INCIDENT_TYPE", type);
        row.put("CATEGORY", category.getId());
        row.put("CATEGORY_LABEL", category.getLabel());
        row.put("IS_MOTOR", "NO");
        row.put("UNITS", "E1, E2");
        row.put("NEIGHBOURHOOD", "Jefferson");
        row.put("WARD", "Mynarski");
        row.put("CALL_TIME", "September 20th, 2026 @ 7:04:01");
        row.put("CALL_TIME_ISO", "2026-09-20T07:04:01-05:00");
        row.put("CLOSED", false);
        row.put("CLOSED_TIME", "");
        row.put("CLOSED_TIME_ISO", "");
        row.put("DURATION", "");
        return row;
    }

    private String render() throws Exception {
        return mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void aFireResponseIsBadgedAndColouredAsAFire() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(incident("2026150713", "Fire Response")));

        String html = render();

        assertThat(html).contains("incident-row incident-row-fire");
        assertThat(html).contains("incident-badge incident-badge-fire");
        assertThat(html).doesNotContain("incident-badge-other");
        // maps.js reads the category from here; without it every marker falls back to grey.
        assertThat(html).contains("\"CATEGORY\":\"fire\"");
    }

    @Test
    void medicalAndOtherCallsKeepTheirOwnGroups() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("1", "Medical Response"),
                incident("2", "Motor Vehicle Incident")));

        String html = render();

        assertThat(html).contains("incident-badge incident-badge-medical");
        assertThat(html).contains("incident-badge incident-badge-other");
        assertThat(html).contains(">Medical Response<");
    }
}
