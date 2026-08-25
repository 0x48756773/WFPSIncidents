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
- `CityOfWinnipegService` — `@Service`; builds the SoQL query (called within `callWindowHours`, and either open or closed within `closedWindowHours`) and fetches JSON from the Winnipeg Open Data API using `$where`/`$order`/`$limit`. The `call_time` bound matters: without it the query also matches every never-closed incident regardless of age — hundreds of stale records that the retention filter discards but that still count against `$limit`
- `Database` — `@Repository`; uses the Spring-managed `DataSource` (HikariCP) via `JdbcTemplate`. Creates/manages the `incidents` table, runs the initial sync in `@PostConstruct`, filters by call time to the service's `callWindowHours` (via `getCallWindowHours()`, so the fetch and this retention filter share one window), and computes a closed flag + on-scene duration per incident
- `ScheduledTasks` — refresh job on `app.refreshSeconds` (`@Scheduled(fixedRateString = "PT${...}S")`). ISO-8601, not a `60s` suffix: duration suffixes in `fixedRateString` need Spring Framework 6.2 and this is 6.1, where they fail the context at **startup**
- `RefreshCadence` — single source for the polling interval. It drives the scheduler, the browser reload timer and six places of user-facing copy, so changing the cadence cannot leave the page describing an old one. Note it is *our polling* interval, not the City's *publishing* interval (five minutes) — the two are stated separately on `/about`. A plain `@Component`, so `@WebMvcTest` slices must `@Import` it
- `SeoController` — renders `/robots.txt` and `/sitemap.xml`. These were static files until the site moved to `wfps.redspectrum.ca`; serving them lets both derive from `app.baseUrl`. The sitemap's `lastmod` comes from `Database.getLastSuccessfulSync()` and is **omitted** rather than guessed before the first successful sync. `changefreq`/`priority` are deliberately absent — Google ignores both
- `AboutController` — renders `/about`. The retention windows it quotes are read from `CityOfWinnipegService` rather than written into the copy, so the prose cannot drift from actual behaviour
- `FeedController` — Atom feed at `/feed.xml`. Atom rather than RSS because an incident changes after publication (it closes), and Atom separates `published` from `updated`. Field values are escaped: one stray `&` from the upstream feed would make the whole document unparseable. Capped at 50 entries
- `SiteIdentityAdvice` — `@ControllerAdvice` publishing `baseUrl`, `authorName`, `contactUser` and `contactDomain` to every view, so no template hardcodes the domain. The contact address is split in two because `maps.js` assembles it in the browser to avoid emitting a harvestable address in the HTML

**Frontend** (`index.html` + `static/scripts/maps.js`):
- All map/table logic lives in the static, cacheable `maps.js`. The template renders only a small `window.WFPS_DATA` blob with the server-side incident list; `maps.js` reads it.
- Leaflet.js map with OpenStreetMap tiles
- Neighbourhoods are resolved to lat/lng via a hardcoded lookup table in `maps.js`, whose 237 entries correspond exactly to the 237 features in the boundary GeoJSON. Nominatim remains as a fallback for a neighbourhood absent from both (e.g. if the city adds one), but no value the feed currently emits reaches it.
- The feed uses `Outside Winnipeg` and `Unverified` in place of a neighbourhood when it has no usable location. These are not places: they are never geocoded and never plotted, since guessing a position would invent an incident location. Keep them out of `neighbourhoodCentres`.
- Incidents are only located to a neighbourhood, so markers are offset ("jittered") around its centre to stop them stacking. The neighbourhood boundary GeoJSON (`data.winnipeg.ca/resource/8k6x-xxsy.geojson`, also used for the outline overlay) is loaded before plotting and the offset is shrunk until the marker falls inside the boundary — otherwise markers land in the wrong neighbourhood. If a neighbourhood has no boundary (or the fetch fails), the offset is applied unconstrained.
- Incidents are colour-coded by category: Fire Rescue, Medical Response, Other. Recently-closed incidents render with muted/dashed markers and a "Closed · <duration>" status badge, and can be hidden via the "Recently Closed" toggle (persisted in `localStorage`).
- Category and closed filters hide both the map marker and the table row. Clicking a table row pans/highlights the corresponding marker, and vice versa.

**Database:** Configured via `spring.datasource.*` in `application.properties` (embedded Derby at `jdbc:derby:incidents;create=true`). Files live in the `incidents/` directory at the project root (not committed). No migrations — the table is created on first run by `Database.createIncidentsTable()`.

**Configuration:** `application.properties` holds the `secret.cityOfWinnipeg` API token. **This file is currently tracked in git and the token is exposed — it needs rotating and untracking.** Site identity: `app.baseUrl` (canonical public origin, no trailing slash — every absolute URL the app emits derives from it, so a domain move is a one-line change) `app.contactEmail` (footer address; independent of `app.baseUrl`, since the mailbox need not follow the site), `app.authorName` (named attribution on `/about`, in the JSON-LD and in the feed — one value, three surfaces), and `app.refreshSeconds` (polling interval; see `RefreshCadence`). Tunables: `endpoint.cityOfWinnipeg.limit` (max records per fetch), `endpoint.cityOfWinnipeg.closedWindowHours` (how far back to include closed incidents), and `endpoint.cityOfWinnipeg.callWindowHours` (retention window by call time, default 24; bounds both the fetch and the display filter).
