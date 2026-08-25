package ca.jdsecurity.incidents.database;

import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private static final ZoneId WINNIPEG = ZoneId.of("America/Winnipeg");

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE incidents (" +
                    "incident_number VARCHAR(255) PRIMARY KEY," +
                    "incident_type VARCHAR(255)," +
                    "is_motor VARCHAR(255)," +
                    "units VARCHAR(255)," +
                    "neighbourhood VARCHAR(255)," +
                    "ward VARCHAR(255)," +
                    "call_time VARCHAR(255)," +
                    "closed_time VARCHAR(255))";

    private static final String INSERT_SQL =
            "INSERT INTO incidents (incident_number, incident_type, is_motor, units, neighbourhood, ward, call_time, closed_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * Minimum gap between upstream fetches triggered by a page request. Without it, an
     * outage turns every page load into its own blocking call to a failing API.
     */
    private static final Duration RECOVERY_COOLDOWN = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    private final CityOfWinnipegService cityOfWinnipegService;
    private final ReentrantLock syncLock = new ReentrantLock();
    private volatile boolean dataSourceAvailable = true;
    private volatile ZonedDateTime lastSuccessfulSync;
    private volatile Instant lastSyncAttempt = Instant.EPOCH;

    public Database(JdbcTemplate jdbc, CityOfWinnipegService cityOfWinnipegService) {
        this.jdbc = jdbc;
        this.cityOfWinnipegService = cityOfWinnipegService;
    }

    public boolean isDataSourceAvailable() {
        return dataSourceAvailable;
    }

    /**
     * When the incident table was last rebuilt from a successful fetch, or {@code null}
     * if no sync has succeeded yet. Drives the sitemap's {@code lastmod}, which is the
     * one freshness hint in a sitemap that Google actually reads.
     */
    public ZonedDateTime getLastSuccessfulSync() {
        return lastSuccessfulSync;
    }

    @PostConstruct
    public void initialize() {
        createIncidentsTable();
        syncIncidentsTableSafe();
    }

    public void createIncidentsTable() {
        if (!tableExists()) {
            log.info("Creating incident table");
            jdbc.execute(CREATE_TABLE_SQL);
            return;
        }
        // Table already exists (e.g. created by an older version) — bring its schema
        // up to date so the app self-heals instead of failing on a missing column.
        log.info("Incident table already exists; checking for schema migrations");
        ensureColumn("closed_time", "VARCHAR(255)");
    }

    private boolean tableExists() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            // Scope the lookup to the connection's current schema so it stays consistent
            // with the unqualified DDL/DML the rest of the class issues.
            try (ResultSet tables = connection.getMetaData().getTables(null, connection.getSchema(), "INCIDENTS", new String[]{"TABLE"})) {
                return tables.next();
            }
        }));
    }

    private void ensureColumn(String column, String ddlType) {
        boolean columnExists = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet columns = connection.getMetaData().getColumns(null, connection.getSchema(), "INCIDENTS", column.toUpperCase())) {
                return columns.next();
            }
        }));
        if (columnExists) {
            return;
        }
        log.info("Adding missing column '{}' to incidents table", column);
        jdbc.execute("ALTER TABLE incidents ADD COLUMN " + column + " " + ddlType);
    }

    public void syncIncidentsTable() throws Exception {
        log.info("Starting City of Winnipeg incident sync");

        // Fetch first — if the API is down this throws before we touch the DB,
        // so existing data is preserved rather than wiped.
        List<HashMap<String, Object>> incidentListing = cityOfWinnipegService.getAllIncidents();

        List<Object[]> batch = new ArrayList<>();
        for (Map<String, Object> incident : incidentListing) {
            String callTime = (String) incident.get("call_time");
            if (!isWithinCallWindow(callTime)) {
                continue;
            }
            batch.add(new Object[]{
                    incident.get("incident_number"),
                    incident.get("incident_type"),
                    incident.get("motor_vehicle_incident"),
                    incident.get("units"),
                    incident.get("neighbourhood"),
                    incident.get("ward"),
                    callTime,
                    incident.get("closed_time")
            });
        }

        jdbc.update("DELETE FROM incidents");
        jdbc.batchUpdate(INSERT_SQL, batch);

        dataSourceAvailable = true;
        lastSuccessfulSync = ZonedDateTime.now(WINNIPEG);
        log.info("City of Winnipeg incident sync completed ({} incidents)", batch.size());
    }

    /** Background sync (startup and the scheduled job). Waits its turn if one is already running. */
    public void syncIncidentsTableSafe() {
        syncLock.lock();
        try {
            runSyncRecordingAttempt();
        } finally {
            syncLock.unlock();
        }
    }

    /**
     * Best-effort sync for a request that found an empty table. Returns immediately —
     * without contacting the API — if another sync is in flight or one was attempted
     * within {@link #RECOVERY_COOLDOWN}.
     *
     * <p>The empty-table case is not "startup hasn't finished": {@code @PostConstruct} runs
     * before the app accepts traffic, so by the time a request arrives the initial sync has
     * already run. An empty table therefore means that sync <em>failed</em> — precisely when
     * retrying on every request is most harmful. Unbounded, it makes each page load block on
     * a failing upstream call, which slows crawlers to the point of depressing crawl rate.
     */
    public void tryRecoverySync() {
        if (attemptedRecently() || !syncLock.tryLock()) {
            return;
        }
        try {
            // Re-check under the lock: a sync may have completed while we waited to acquire it.
            if (attemptedRecently()) {
                return;
            }
            runSyncRecordingAttempt();
        } finally {
            syncLock.unlock();
        }
    }

    private boolean attemptedRecently() {
        return Duration.between(lastSyncAttempt, Instant.now()).compareTo(RECOVERY_COOLDOWN) < 0;
    }

    /** Caller must hold {@link #syncLock}. Records the attempt before it runs, so a slow failure still opens the breaker. */
    private void runSyncRecordingAttempt() {
        lastSyncAttempt = Instant.now();
        try {
            syncIncidentsTable();
        } catch (Exception e) {
            dataSourceAvailable = false;
            log.error("Incident sync failed (data source may be unavailable): {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getRecentIncidents() {
        log.info("Retrieving all incidents from database");
        return jdbc.query("SELECT * FROM incidents ORDER BY call_time DESC", (rs, rowNum) -> {
            String rawCallTime = rs.getString("call_time");
            String rawClosedTime = rs.getString("closed_time");
            boolean closed = rawClosedTime != null && !rawClosedTime.isBlank();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("INCIDENT_NUMBER", rs.getString("incident_number"));
            row.put("INCIDENT_TYPE", rs.getString("incident_type"));
            row.put("IS_MOTOR", rs.getString("is_motor"));
            row.put("UNITS", rs.getString("units"));
            row.put("NEIGHBOURHOOD", rs.getString("neighbourhood"));
            row.put("WARD", rs.getString("ward"));
            row.put("CALL_TIME", formatCallTime(rawCallTime));
            // Machine-readable counterparts, for the feed and for <time datetime> markup.
            row.put("CALL_TIME_ISO", toIso(rawCallTime));
            row.put("CLOSED", closed);
            row.put("CLOSED_TIME", closed ? formatCallTime(rawClosedTime) : "");
            row.put("CLOSED_TIME_ISO", closed ? toIso(rawClosedTime) : "");
            row.put("DURATION", closed ? formatDuration(rawCallTime, rawClosedTime) : "");
            return row;
        });
    }

    // Retention window, taken from the service so the fetch and this filter cannot drift apart:
    // anything the query returns is displayable, and anything displayable the query asks for.
    private boolean isWithinCallWindow(String callTime) {
        ZonedDateTime parsed = parse(callTime);
        if (parsed == null) {
            return false;
        }
        Duration age = Duration.between(parsed, ZonedDateTime.now(WINNIPEG));
        Duration window = Duration.ofHours(cityOfWinnipegService.getCallWindowHours());
        return !age.isNegative() && age.compareTo(window) <= 0;
    }

    private String formatCallTime(String timestamp) {
        ZonedDateTime parsed = parse(timestamp);
        if (parsed == null) {
            return timestamp;
        }
        int day = parsed.getDayOfMonth();
        String suffix;
        if (day >= 11 && day <= 13) {
            suffix = "th";
        } else {
            suffix = switch (day % 10) {
                case 1 -> "st";
                case 2 -> "nd";
                case 3 -> "rd";
                default -> "th";
            };
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d'" + suffix + "', yyyy '@ 'H:mm:ss");
        return parsed.format(formatter);
    }

    private String toIso(String timestamp) {
        ZonedDateTime parsed = parse(timestamp);
        return parsed == null ? "" : parsed.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String formatDuration(String callTime, String closedTime) {
        ZonedDateTime start = parse(callTime);
        ZonedDateTime end = parse(closedTime);
        if (start == null || end == null) {
            return "";
        }
        Duration duration = Duration.between(start, end);
        if (duration.isNegative()) {
            return "";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    /**
     * Parses either an offset timestamp or a floating (zone-less) one, anchoring the
     * latter to Winnipeg local time. Returns {@code null} when the value can't be parsed.
     */
    private ZonedDateTime parse(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).atZoneSameInstant(WINNIPEG);
        } catch (DateTimeParseException offsetMiss) {
            try {
                return LocalDateTime.parse(timestamp).atZone(WINNIPEG);
            } catch (DateTimeParseException localMiss) {
                return null;
            }
        }
    }
}
