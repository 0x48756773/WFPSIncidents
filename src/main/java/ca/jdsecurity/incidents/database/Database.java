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

    private final JdbcTemplate jdbc;
    private final CityOfWinnipegService cityOfWinnipegService;
    private volatile boolean dataSourceAvailable = true;

    public Database(JdbcTemplate jdbc, CityOfWinnipegService cityOfWinnipegService) {
        this.jdbc = jdbc;
        this.cityOfWinnipegService = cityOfWinnipegService;
    }

    public boolean isDataSourceAvailable() {
        return dataSourceAvailable;
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
            if (!isWithinLast24Hours(callTime)) {
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
        log.info("City of Winnipeg incident sync completed ({} incidents)", batch.size());
    }

    public void syncIncidentsTableSafe() {
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
            row.put("CLOSED", closed);
            row.put("CLOSED_TIME", closed ? formatCallTime(rawClosedTime) : "");
            row.put("DURATION", closed ? formatDuration(rawCallTime, rawClosedTime) : "");
            return row;
        });
    }

    private boolean isWithinLast24Hours(String callTime) {
        ZonedDateTime parsed = parse(callTime);
        if (parsed == null) {
            return false;
        }
        Duration age = Duration.between(parsed, ZonedDateTime.now(WINNIPEG));
        return !age.isNegative() && age.compareTo(Duration.ofHours(24)) <= 0;
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
