package ca.jdsecurity.incidents.database;

import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseRecoverySyncTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private CityOfWinnipegService cityOfWinnipegService;

    /**
     * The regression this guards: an outage used to make every page load fire its own
     * blocking upstream call, because the controller retried on any empty result.
     */
    @Test
    void repeatedRequestsDuringAnOutageContactTheApiOnce() throws Exception {
        when(cityOfWinnipegService.getAllIncidents()).thenThrow(new RuntimeException("upstream down"));
        Database database = new Database(jdbc, cityOfWinnipegService);

        for (int request = 0; request < 25; request++) {
            database.tryRecoverySync();
        }

        verify(cityOfWinnipegService, times(1)).getAllIncidents();
        assertThat(database.isDataSourceAvailable()).isFalse();
    }

    /** Concurrent requests must not each get their own upstream call either. */
    @Test
    void concurrentRequestsContactTheApiOnce() throws Exception {
        when(cityOfWinnipegService.getAllIncidents()).thenThrow(new RuntimeException("upstream down"));
        Database database = new Database(jdbc, cityOfWinnipegService);

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    database.tryRecoverySync();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        verify(cityOfWinnipegService, times(1)).getAllIncidents();
    }

    /** A failed sync must not advertise a sync time the sitemap would then report as lastmod. */
    @Test
    void failedSyncLeavesLastSuccessfulSyncUnset() throws Exception {
        when(cityOfWinnipegService.getAllIncidents()).thenThrow(new RuntimeException("upstream down"));
        Database database = new Database(jdbc, cityOfWinnipegService);

        database.tryRecoverySync();

        assertThat(database.getLastSuccessfulSync()).isNull();
    }
}
