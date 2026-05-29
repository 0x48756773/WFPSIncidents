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

    @Scheduled(cron = "0 */5 * * * ?")
    public void refreshIncidents() {
        database.syncIncidentsTableSafe();
    }
}
