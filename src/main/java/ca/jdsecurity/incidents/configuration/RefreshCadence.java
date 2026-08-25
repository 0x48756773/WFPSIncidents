package ca.jdsecurity.incidents.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How often this site polls the City for new incidents.
 *
 * <p>The interval is quoted in six places of user-facing copy as well as driving the scheduler
 * and the browser's reload timer. Centralising it means changing the cadence cannot leave the
 * page describing one it no longer has.
 *
 * <p>Note this is <em>our</em> polling interval, not the City's publishing interval. WFPS
 * publishes on a five-minute cycle; polling faster does not make the data fresher than its
 * source, it shortens the lag between the City publishing a call and this site showing it.
 */
@Component
public class RefreshCadence {

    private final int seconds;

    public RefreshCadence(@Value("${app.refreshSeconds:60}") int seconds) {
        if (seconds < 1) {
            throw new IllegalArgumentException("app.refreshSeconds must be at least 1, got " + seconds);
        }
        this.seconds = seconds;
    }

    public int getSeconds() {
        return seconds;
    }

    /** Reads after the word "every": "minute", "5 minutes", "30 seconds". */
    public String getLabel() {
        if (seconds % 60 != 0) {
            return seconds + " seconds";
        }
        int minutes = seconds / 60;
        return minutes == 1 ? "minute" : minutes + " minutes";
    }
}
