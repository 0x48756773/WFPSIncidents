package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ca.jdsecurity.incidents.configuration.RefreshCadence;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The title and description each appear three times in the head — plain, Open Graph and
 * Twitter. They were three separate literals and had already drifted apart; these tests
 * hold them together without pinning the wording, so the copy can still be rewritten freely.
 */
@WebMvcTest(AppController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author"
})
class PageMetadataTest {

    /** Google truncates the displayed title around here; beyond it the tail is simply lost. */
    private static final int TITLE_DISPLAY_LIMIT = 60;
    private static final int DESCRIPTION_DISPLAY_LIMIT = 160;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private String html;

    @BeforeEach
    void render() throws Exception {
        html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
    }

    private String first(String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(html);
        assertThat(matcher.find()).as("no match for %s", regex).isTrue();
        return matcher.group(1);
    }

    private List<String> titles() {
        return List.of(
                first("<title[^>]*>([^<]+)</title>"),
                first("property=\"og:title\" content=\"([^\"]+)\""),
                first("name=\"twitter:title\" content=\"([^\"]+)\""));
    }

    private List<String> descriptions() {
        return List.of(
                first("name=\"description\" content=\"([^\"]+)\""),
                first("property=\"og:description\" content=\"([^\"]+)\""),
                first("name=\"twitter:description\" content=\"([^\"]+)\""));
    }

    @Test
    void everyTitleCopyIsTheSame() {
        assertThat(titles()).doesNotContainNull().containsOnly(titles().get(0));
    }

    @Test
    void everyDescriptionCopyIsTheSame() {
        assertThat(descriptions()).doesNotContainNull().containsOnly(descriptions().get(0));
    }

    @Test
    void titleAndDescriptionFitInSearchResults() {
        assertThat(titles().get(0)).hasSizeLessThanOrEqualTo(TITLE_DISPLAY_LIMIT);
        assertThat(descriptions().get(0)).hasSizeLessThanOrEqualTo(DESCRIPTION_DISPLAY_LIMIT);
    }

    /** WFPS is the term the site already ranks for; a rewrite must not drop it from the title. */
    @Test
    void titleKeepsTheAcronymTheSiteRanksFor() {
        assertThat(titles().get(0)).contains("WFPS");
    }
}
