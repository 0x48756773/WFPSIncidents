package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeoController.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test"
})
class SeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    @Test
    void robotsPointsSitemapAtConfiguredOrigin() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Sitemap: https://example.test/sitemap.xml")));
    }

    @Test
    void sitemapUsesConfiguredOriginAndReportsLastSync() throws Exception {
        when(database.getLastSuccessfulSync()).thenReturn(
                ZonedDateTime.of(2026, 8, 25, 9, 30, 0, 0, ZoneId.of("America/Winnipeg")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<loc>https://example.test/</loc>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<lastmod>2026-08-25T09:30:00-05:00</lastmod>")));
    }

    /** A guessed timestamp is worse than none, so lastmod is omitted until a sync succeeds. */
    @Test
    void sitemapOmitsLastmodBeforeFirstSuccessfulSync() throws Exception {
        when(database.getLastSuccessfulSync()).thenReturn(null);

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<lastmod>"))));
    }

    /** Both are ignored by Google; emitting them implies a control we do not have. */
    @Test
    void sitemapOmitsChangefreqAndPriority() throws Exception {
        when(database.getLastSuccessfulSync()).thenReturn(null);

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<changefreq>"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<priority>"))));
    }
}
