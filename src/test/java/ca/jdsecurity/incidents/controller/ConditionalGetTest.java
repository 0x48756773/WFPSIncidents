package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ca.jdsecurity.incidents.configuration.RefreshCadence;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test"
})
class ConditionalGetTest {

    private static final ZonedDateTime SYNCED_AT =
            ZonedDateTime.of(2026, 8, 25, 9, 30, 0, 0, ZoneId.of("America/Winnipeg"));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private String etagFor(boolean sourceAvailable) throws Exception {
        when(database.getLastSuccessfulSync()).thenReturn(SYNCED_AT);
        when(database.isDataSourceAvailable()).thenReturn(sourceAvailable);
        return mockMvc.perform(get("/")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    @Test
    void unchangedPageRevalidatesAsNotModified() throws Exception {
        String etag = etagFor(true);
        assertThat(etag).isNotBlank();

        mockMvc.perform(get("/").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    /**
     * A failed refresh leaves the last *successful* sync untouched. Keyed on that alone the
     * page would revalidate as unchanged and the "data source unavailable" banner would never
     * reach the reader, so availability has to be part of the tag too.
     */
    @Test
    void losingTheDataSourceChangesTheTag() throws Exception {
        String whenUp = etagFor(true);
        String whenDown = etagFor(false);

        assertThat(whenDown).isNotEqualTo(whenUp);
    }

    @Test
    void staleTagStillGetsAFreshPage() throws Exception {
        etagFor(true);

        mockMvc.perform(get("/").header(HttpHeaders.IF_NONE_MATCH, "\"an-older-sync-up\""))
                .andExpect(status().isOk());
    }

    /** No successful sync means nothing to revalidate against; never invent a tag. */
    @Test
    void noEtagBeforeFirstSuccessfulSync() throws Exception {
        when(database.getLastSuccessfulSync()).thenReturn(null);

        String etag = mockMvc.perform(get("/")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(etag).isNull();
    }
}
