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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * CARTO's basemaps now reject unkeyed tile requests, so the dark map is only as good as the
 * key reaching the browser. Nothing on the page fails loudly when it does not — dark mode
 * simply renders empty tiles — so the wiring from configuration through to WFPS_DATA is
 * asserted here rather than left to be noticed by someone toggling the theme.
 */
@WebMvcTest(AppController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author",
        "app.mapTilesKey=test_tiles_key"
})
class MapTilesKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    @Test
    void configuredTileKeyReachesThePage() throws Exception {
        String html = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("mapTilesKey: \"test_tiles_key\"");
    }
}
