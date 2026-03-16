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

**Request flow:** Every 5 minutes, `ScheduledTasks` calls `CityOfWinnipegService` (Unirest HTTP client, API token from `application.properties`) → syncs results into Apache Derby embedded database (`Database.java`) → `AppController` queries the DB and passes incidents to the Thymeleaf `index.html` template.

**Key classes:**
- `AppController` — single `GET /` route; also triggers initial data load on startup
- `CityOfWinnipegService` — fetches JSON from Winnipeg Open Data API
- `Database` — JDBC + embedded Derby; creates/manages the `incidents` table; filters to last 24 hours
- `ScheduledTasks` — `@Scheduled(cron = "0 */5 * * * ?")` refresh job

**Frontend** (`index.html` + `static/scripts/maps.js`):
- Leaflet.js map with OpenStreetMap tiles
- Neighbourhoods are resolved to lat/lng via a hardcoded lookup table in `maps.js` (no geocoding API calls)
- Incidents are colour-coded by category: Fire Rescue, Medical Response, Other
- Clicking a table row pans/highlights the corresponding map marker, and vice versa

**Database:** Derby database files are stored in the `incidents/` directory at the project root (not committed). No migrations — table is created on first run via `Database.java`.

**Configuration:** Sensitive values (`secret.cityOfWinnipeg` API token) live in `application.properties`, which is not committed.
