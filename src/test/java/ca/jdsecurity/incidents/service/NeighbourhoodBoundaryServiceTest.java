package ca.jdsecurity.incidents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NeighbourhoodBoundaryServiceTest {

    private static final String GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}";

    @Mock
    private HttpClient http;

    @Mock
    private HttpResponse<String> response;

    private NeighbourhoodBoundaryService service() {
        return new NeighbourhoodBoundaryService("https://example.test/boundaries.geojson", http);
    }

    @SuppressWarnings("unchecked")
    private void respondWith(int status, String body) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void successfulFetchIsCachedWithAnEtag() throws Exception {
        respondWith(200, GEOJSON);
        NeighbourhoodBoundaryService service = service();

        service.refreshIfStale();

        assertThat(service.current()).isPresent();
        assertThat(service.current().orElseThrow().body()).isEqualTo(GEOJSON);
        assertThat(service.current().orElseThrow().etag()).isNotBlank();
    }

    /**
     * The behaviour that matters: stale boundaries are still correct boundaries. Discarding them
     * on a failed refresh would push every marker back to an unconstrained offset.
     */
    @Test
    @SuppressWarnings("unchecked")
    void failedRefreshKeepsThePreviousCopy() throws Exception {
        respondWith(200, GEOJSON);
        NeighbourhoodBoundaryService service = service();
        service.refreshIfStale();

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("upstream down"));
        service.refreshIfStale();

        assertThat(service.current().orElseThrow().body()).isEqualTo(GEOJSON);
    }

    @Test
    void serverErrorLeavesTheCacheEmptyRatherThanStoringAnErrorBody() throws Exception {
        respondWith(500, "<html>Gateway error</html>");

        NeighbourhoodBoundaryService service = service();
        service.refreshIfStale();

        assertThat(service.current()).isEmpty();
    }

    @Test
    void blankBodyIsNotCached() throws Exception {
        respondWith(200, "   ");

        NeighbourhoodBoundaryService service = service();
        service.refreshIfStale();

        assertThat(service.current()).isEmpty();
    }

    /** Boundaries change on the order of years; a fresh copy must not trigger another fetch. */
    @Test
    @SuppressWarnings("unchecked")
    void freshCopyIsNotRefetched() throws Exception {
        respondWith(200, GEOJSON);
        NeighbourhoodBoundaryService service = service();

        service.refreshIfStale();
        service.refreshIfStale();
        service.refreshIfStale();

        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
