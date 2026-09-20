package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.database.Database;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Static assets are served with a 30-day public cache. On a fixed URL that means a deploy
 * does not reach anyone who has been here before — for up to a month their browser pairs
 * the new markup with the old stylesheet and the new incident data with the old maps.js.
 * Exactly that happened: a released fix rendered unstyled for returning visitors.
 *
 * <p>The content hash is what makes the long cache safe, and it only appears on URLs built
 * with {@code @{...}}. A stylesheet written as a plain {@code href} silently opts out and
 * looks completely normal in review, so these tests pin the rendered URLs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author",
        "app.analyticsId="
})
class AssetFingerprintingTest {

    /** e.g. /css/main-9f86d081884c7d65.css — Spring's content strategy uses an MD5 hex digest. */
    private static final String HASHED_CSS = "/css/main-[0-9a-f]{32}\\.css";
    private static final String HASHED_JS = "/scripts/maps-[0-9a-f]{32}\\.js";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private String render(String path) throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of());
        return mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theMapPageAsksForFingerprintedAssets() throws Exception {
        String html = render("/");

        assertThat(html).containsPattern(HASHED_CSS);
        assertThat(html).containsPattern(HASHED_JS);
        // The unhashed name is the one a month-old cache already holds.
        assertThat(html).doesNotContain("\"/css/main.css\"");
        assertThat(html).doesNotContain("\"/scripts/maps.js\"");
    }

    @Test
    void theAboutPageAsksForThemToo() throws Exception {
        assertThat(render("/about")).containsPattern(HASHED_CSS);
    }

    /**
     * The service worker and the manifest must stay on stable URLs: a worker is identified
     * by its URL, so a name that changes every deploy orphans the registered one, and sw.js
     * fetches /offline.html by that literal path.
     */
    @Test
    void theServiceWorkerAndManifestStayOnStableUrls() throws Exception {
        String html = render("/");

        assertThat(html).contains("/site.webmanifest");
        assertThat(html).doesNotContainPattern("/site-[0-9a-f]{32}\\.webmanifest");
        assertThat(html).doesNotContainPattern("/sw-[0-9a-f]{32}\\.js");
    }
}
