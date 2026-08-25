package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.service.NeighbourhoodBoundaryService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Duration;
import java.util.Optional;

/**
 * Serves the cached neighbourhood boundaries from this origin.
 *
 * <p>Never triggers an upstream fetch: the cache is filled on the scheduler, so a request either
 * gets what is already held or a 503. {@code maps.js} treats a failed boundary load as "place
 * markers without constraining them", so a 503 degrades rather than breaks.
 */
@Controller
public class BoundaryController {

    /** Matches the service's TTL, so a browser rarely re-requests at all. */
    private static final Duration BROWSER_TTL = Duration.ofHours(24);

    private final NeighbourhoodBoundaryService boundaries;

    public BoundaryController(NeighbourhoodBoundaryService boundaries) {
        this.boundaries = boundaries;
    }

    @GetMapping("/data/neighbourhoods.geojson")
    public ResponseEntity<String> neighbourhoods() {
        Optional<NeighbourhoodBoundaryService.Snapshot> snapshot = boundaries.current();
        if (snapshot.isEmpty()) {
            // No copy yet — do not make the caller wait on the City for one.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        NeighbourhoodBoundaryService.Snapshot current = snapshot.get();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(BROWSER_TTL).cachePublic())
                .eTag(current.etag())
                .contentType(MediaType.valueOf("application/geo+json"))
                .body(current.body());
    }
}
