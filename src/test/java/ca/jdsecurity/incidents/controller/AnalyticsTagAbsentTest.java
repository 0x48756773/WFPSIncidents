package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.database.Database;
import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * With no measurement ID configured, no tag is emitted at all. That is what makes it possible
 * to run a deployment that does not report into the live property — a staging environment, or
 * a local run — which a hardcoded ID in the template did not allow.
 */
@WebMvcTest({AppController.class, AboutController.class})
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author",
        "app.analyticsId="
})
class AnalyticsTagAbsentTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    @MockBean
    private CityOfWinnipegService cityOfWinnipegService;

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theMapPageEmitsNoTagWhenNoneIsConfigured() throws Exception {
        assertThat(render("/")).doesNotContain("googletagmanager").doesNotContain("gtag(");
    }

    @Test
    void theAboutPageEmitsNoTagWhenNoneIsConfigured() throws Exception {
        assertThat(render("/about")).doesNotContain("googletagmanager").doesNotContain("gtag(");
    }
}
