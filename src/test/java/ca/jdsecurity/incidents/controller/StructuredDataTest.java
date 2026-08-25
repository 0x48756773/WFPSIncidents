package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ca.jdsecurity.incidents.configuration.RefreshCadence;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(AppController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test"
})
class StructuredDataTest {

    private static final Pattern LD_JSON =
            Pattern.compile("<script type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private JsonNode graph;

    @BeforeEach
    void renderPage() throws Exception {
        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        Matcher matcher = LD_JSON.matcher(html);
        assertThat(matcher.find()).as("page must carry a JSON-LD block").isTrue();
        graph = new ObjectMapper().readTree(matcher.group(1)).get("@graph");
    }

    private JsonNode node(String type) {
        for (JsonNode n : graph) {
            if (type.equals(n.path("@type").asText())) {
                return n;
            }
        }
        throw new AssertionError("no " + type + " node in @graph");
    }

    /** Freshness must be machine-readable, not only painted in by client-side JS. */
    @Test
    void lastUpdatedRendersAsAMachineReadableTimeElement() throws Exception {
        org.mockito.Mockito.when(database.getLastSuccessfulSync()).thenReturn(
                ZonedDateTime.of(2026, 8, 25, 9, 30, 0, 0, ZoneId.of("America/Winnipeg")));

        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("datetime=\"2026-08-25T09:30:00-05:00\"");
    }

    /** Before the first successful sync there is no honest timestamp to show, so show none. */
    @Test
    void lastUpdatedIsAbsentBeforeAnySuccessfulSync() throws Exception {
        org.mockito.Mockito.when(database.getLastSuccessfulSync()).thenReturn(null);

        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("page-updated");
    }

    @Test
    void structuredDataIsValidJsonAndUsesTheConfiguredOrigin() {
        assertThat(node("WebApplication").path("url").asText()).isEqualTo("https://example.test/");
    }

    /**
     * The bug this guards: provider named the Winnipeg Fire Paramedic Service, asserting they
     * provide this application. WFPS may only ever be the subject, never the provider.
     */
    @Test
    void wfpsIsTheSubjectNotTheProvider() {
        JsonNode app = node("WebApplication");
        assertThat(app.has("provider")).as("provider must not be present at all").isFalse();
        assertThat(app.path("about").path("name").asText()).contains("Winnipeg Fire Paramedic Service");
        assertThat(node("Person").path("name").asText()).isNotEmpty();
    }

    /** We are a third-party view of open data. Claiming otherwise invites use as a reporting channel. */
    @Test
    void siteNeverClaimsToBeAnEmergencyOrGovernmentService() {
        for (JsonNode n : graph) {
            assertThat(n.path("@type").asText())
                    .isNotIn(List.of("EmergencyService", "GovernmentService", "GovernmentOffice", "FireStation"));
        }
        // The City of Winnipeg is legitimately a GovernmentOrganization — but only as the data's creator.
        assertThat(node("Dataset").path("creator").path("@type").asText()).isEqualTo("GovernmentOrganization");
    }

    /** Schema.org Dataset has no updateFrequency property; emitting one is noise, not signal. */
    @Test
    void datasetOmitsNonExistentUpdateFrequencyProperty() {
        assertThat(node("Dataset").has("updateFrequency")).isFalse();
    }
}
