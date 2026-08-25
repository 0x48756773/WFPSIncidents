package ca.jdsecurity.incidents.tasks;

import ca.jdsecurity.incidents.database.Database;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private final Database database;

    public ScheduledTasks(Database database) {
        this.database = database;
    }

    // Interval comes from app.refreshSeconds so the scheduler, the page copy and the
    // browser's reload timer cannot disagree about the cadence. ISO-8601 rather than a "60s"
    // suffix: duration suffixes in fixedRateString need Spring Framework 6.2, and this is 6.1.
    @Scheduled(fixedRateString = "PT${app.refreshSeconds:60}S")
    public void refreshIncidents() {
        database.syncIncidentsTableSafe();
    }
}
