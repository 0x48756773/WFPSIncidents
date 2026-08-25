package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.configuration.RefreshCadence;
import ca.jdsecurity.incidents.service.NeighbourhoodBoundaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoundaryController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author"
})
class BoundaryControllerTest {

    private static final String GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}";
    private static final String ETAG = "\"abc123-42\"";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NeighbourhoodBoundaryService boundaries;

    private void cacheHolds(String body) {
        when(boundaries.current()).thenReturn(Optional.of(
                new NeighbourhoodBoundaryService.Snapshot(body, Instant.now(), ETAG)));
    }

    @Test
    void servesTheCachedDocumentWithALongCacheLifetime() throws Exception {
        cacheHolds(GEOJSON);

        mockMvc.perform(get("/data/neighbourhoods.geojson"))
                .andExpect(status().isOk())
                .andExpect(content().string(GEOJSON))
                .andExpect(header().string(HttpHeaders.ETAG, ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, public"));
    }

    /** The point of the ETag: a reload a minute later revalidates instead of re-downloading. */
    @Test
    void unchangedBoundariesRevalidateAsNotModified() throws Exception {
        cacheHolds(GEOJSON);

        mockMvc.perform(get("/data/neighbourhoods.geojson").header(HttpHeaders.IF_NONE_MATCH, ETAG))
                .andExpect(status().isNotModified());
    }

    /**
     * Never block the caller on the City. maps.js treats a failed boundary load as "place markers
     * without constraining them", so an unavailable cache degrades the map rather than breaking it.
     */
    @Test
    void reportsUnavailableRatherThanFetchingOnDemand() throws Exception {
        when(boundaries.current()).thenReturn(Optional.empty());

        mockMvc.perform(get("/data/neighbourhoods.geojson"))
                .andExpect(status().isServiceUnavailable());
    }
}
