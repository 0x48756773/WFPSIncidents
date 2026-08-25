package ca.jdsecurity.incidents.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caches the City's neighbourhood boundary GeoJSON.
 *
 * <p>The browser used to fetch this straight from data.winnipeg.ca on every page load. That was
 * tolerable when the page reloaded every five minutes; at sixty seconds it is five times the
 * traffic, per open tab, for a file that changes about as often as ward boundaries do.
 *
 * <p>Held in memory and served from this origin so all visitors share one upstream fetch. A failed
 * refresh keeps the previous copy rather than discarding it — stale boundaries are still correct
 * boundaries, and losing them would push every marker back to an unconstrained offset.
 */
@Service
public class NeighbourhoodBoundaryService {

    private static final Logger log = LoggerFactory.getLogger(NeighbourhoodBoundaryService.class);

    /** Boundaries change on the order of years; a day is already conservative. */
    private static final Duration TTL = Duration.ofHours(24);
    /** Guards against an unexpected upstream response consuming the heap. */
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private final HttpClient http;
    private final String url;
    private volatile Snapshot snapshot;

    @Autowired
    public NeighbourhoodBoundaryService(
            @Value("${endpoint.cityOfWinnipeg.host}") String host,
            @Value("${endpoint.cityOfWinnipeg.neighbourhoodsPath}") String path) {
        this(host + path, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Visible for testing: lets the serve-stale behaviour be exercised without a live upstream. */
    NeighbourhoodBoundaryService(String url, HttpClient http) {
        this.url = url;
        this.http = http;
    }

    /** The cached document, or empty if no fetch has ever succeeded. */
    public Optional<Snapshot> current() {
        return Optional.ofNullable(snapshot);
    }

    /**
     * Runs on the scheduler rather than on a request, so no visitor ever waits on the upstream
     * fetch. Fires at startup and hourly, but only actually fetches when the copy is missing or
     * past its TTL — so a failed startup fetch retries within the hour instead of after a day.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    public void refreshIfStale() {
        Snapshot existing = snapshot;
        if (existing != null && Duration.between(existing.fetchedAt(), Instant.now()).compareTo(TTL) < 0) {
            return;
        }
        if (!refreshLock.tryLock()) {
            return;
        }
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Neighbourhood boundary fetch returned HTTP {}; keeping existing copy", response.statusCode());
                return;
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                log.warn("Neighbourhood boundary fetch returned an empty body; keeping existing copy");
                return;
            }
            if (body.length() > MAX_BYTES) {
                log.warn("Neighbourhood boundary response of {} chars exceeds the {} cap; ignoring",
                        body.length(), MAX_BYTES);
                return;
            }
            snapshot = new Snapshot(body, Instant.now(), "\"" + Integer.toHexString(body.hashCode())
                    + "-" + body.length() + "\"");
            log.info("Cached neighbourhood boundaries ({} chars)", body.length());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Neighbourhood boundary fetch interrupted; keeping existing copy");
        } catch (Exception e) {
            // Existing copy is deliberately left in place; see the class comment.
            log.warn("Neighbourhood boundary fetch failed ({}); keeping existing copy", e.getMessage());
        } finally {
            refreshLock.unlock();
        }
    }

    /** An immutable cached document plus the ETag browsers revalidate against. */
    public record Snapshot(String body, Instant fetchedAt, String etag) {
    }
}
