# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./mvnw spring-boot:run          # Run locally
./mvnw clean package            # Build JAR
./mvnw clean package -DskipTests
./mvnw test                     # Run all tests
./mvnw test -Dtest=ClassName    # Run a specific test class
```

The app runs on `http://localhost:8080` by default.

## Architecture

Spring Boot web app that fetches real-time [Winnipeg Fire Paramedic Service](https://data.winnipeg.ca/resource/yg42-q284.json) incidents and displays them on an interactive Leaflet.js map.

**Request flow:** All components share a single Spring-managed `Database` bean. On startup (`@PostConstruct`) and every 5 minutes (`ScheduledTasks`), `Database` calls `CityOfWinnipegService` (Unirest HTTP client, API token from `application.properties`) → syncs results into the Apache Derby embedded database → `AppController` queries the DB and passes incidents to the Thymeleaf `index.html` template.

**Key classes** (all wired via Spring constructor injection — no manual `new`):
- `AppController` — single `GET /` route; injects `Database`
- `CityOfWinnipegService` — `@Service`; builds the SoQL query (open incidents + those closed within `closedWindowHours`) and fetches JSON from the Winnipeg Open Data API using `$where`/`$order`/`$limit`
- `Database` — `@Repository`; uses the Spring-managed `DataSource` (HikariCP) via `JdbcTemplate`. Creates/manages the `incidents` table, runs the initial sync in `@PostConstruct`, filters to the last 24 hours by call time, and computes a closed flag + on-scene duration per incident
- `ScheduledTasks` — `@Scheduled(cron = "0 */5 * * * ?")` refresh job

**Frontend** (`index.html` + `static/scripts/maps.js`):
- All map/table logic lives in the static, cacheable `maps.js`. The template renders only a small `window.WFPS_DATA` blob with the server-side incident list; `maps.js` reads it.
- Leaflet.js map with OpenStreetMap tiles
- Neighbourhoods are resolved to lat/lng via a hardcoded lookup table in `maps.js`, whose 237 entries correspond exactly to the 237 features in the boundary GeoJSON. Nominatim remains as a fallback for a neighbourhood absent from both (e.g. if the city adds one), but no value the feed currently emits reaches it.
- The feed uses `Outside Winnipeg` and `Unverified` in place of a neighbourhood when it has no usable location. These are not places: they are never geocoded and never plotted, since guessing a position would invent an incident location. Keep them out of `neighbourhoodCentres`.
- Incidents are only located to a neighbourhood, so markers are offset ("jittered") around its centre to stop them stacking. The neighbourhood boundary GeoJSON (`data.winnipeg.ca/resource/8k6x-xxsy.geojson`, also used for the outline overlay) is loaded before plotting and the offset is shrunk until the marker falls inside the boundary — otherwise markers land in the wrong neighbourhood. If a neighbourhood has no boundary (or the fetch fails), the offset is applied unconstrained.
- Incidents are colour-coded by category: Fire Rescue, Medical Response, Other. Recently-closed incidents render with muted/dashed markers and a "Closed · <duration>" status badge, and can be hidden via the "Recently Closed" toggle (persisted in `localStorage`).
- Category and closed filters hide both the map marker and the table row. Clicking a table row pans/highlights the corresponding marker, and vice versa.

**Database:** Configured via `spring.datasource.*` in `application.properties` (embedded Derby at `jdbc:derby:incidents;create=true`). Files live in the `incidents/` directory at the project root (not committed). No migrations — the table is created on first run by `Database.createIncidentsTable()`.

**Configuration:** Sensitive values (`secret.cityOfWinnipeg` API token) live in `application.properties`, which is not committed. Tunables: `endpoint.cityOfWinnipeg.limit` (max records per fetch) and `endpoint.cityOfWinnipeg.closedWindowHours` (how far back to include closed incidents).
