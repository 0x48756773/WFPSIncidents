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
 * The bug this guards: the analytics tag was pasted into index.html and nowhere else, so
 * /about was never measured. It drew search impressions of its own and reported no visitors
 * at all, which read as "nobody goes there" rather than "nothing there is counted".
 *
 * <p>Every page that is served to a reader has to carry the tag, and the only way to keep
 * that true as pages are added is to assert it.
 */
@WebMvcTest({AppController.class, AboutController.class})
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author",
        "app.analyticsId=G-TESTID123"
})
class AnalyticsTagTest {

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
    void theMapPageCarriesTheTag() throws Exception {
        String html = render("/");

        assertThat(html).contains("https://www.googletagmanager.com/gtag/js?id=G-TESTID123");
        assertThat(html).contains("gtag('config', \"G-TESTID123\")");
    }

    @Test
    void theAboutPageCarriesTheTagToo() throws Exception {
        String html = render("/about");

        assertThat(html).contains("https://www.googletagmanager.com/gtag/js?id=G-TESTID123");
        assertThat(html).contains("gtag('config', \"G-TESTID123\")");
    }

    /** One configured value, not one literal per template, so the two cannot drift apart. */
    @Test
    void bothPagesReportToTheSameProperty() throws Exception {
        assertThat(render("/")).doesNotContain("G-FGQZ0FFFV1");
        assertThat(render("/about")).doesNotContain("G-FGQZ0FFFV1");
    }
}
