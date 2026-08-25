# WFPS Incident Map — SEO Implementation Plan

Companion to [`SEOPlan.md`](SEOPlan.md), which holds the findings register (F-numbers below refer to it).
Branch: `claude/wfps-seo-strategy-31om1q`

## Sequencing logic

Three constraints drive the phase order:

- **Measure before you move.** Baselines are worthless collected after the change.
- **One variable at a time.** The domain migration and the metadata rewrite both move the same
  rankings. Shipped together, neither is attributable.
- **History gates pages.** Programmatic URLs without stored data are empty URLs, and empty URLs at
  scale are a site-level risk.
- **The archive is the point.** `winnipeg.ca` shows only ongoing incidents, so the retrospective
  query space is unoccupied (`SEOPlan.md` §3). Phase 4 is not merely plumbing for Phase 5 — it is
  what unlocks the one category the incumbent cannot serve. Where 5 and 5A compete for time, 5A wins.

```
Phase 0  Baseline & safety
        │
        ├─→ Phase 1  Migration ──→ (settle 2–4 wks) ──→ Phase 2  On-page ──┐
        │                                                                  │
        └─→ Phase 3  Performance ─────────────────────────────────────────┤
                                                                           ▼
                                                              Phase 4  History
                                                                           │
                                          ┌────────────────────────────────┤
                                          ▼                                ▼
                        Phase 5A  Archive  ◀── priority          Phase 5  Entity pages
                                          │                                │
                                          └────────────┬───────────────────┘
                                                       ▼
                                          Phase 6  Authority & measurement
```

Phase 3 is independent of the migration and can run in parallel with Phases 1–2.

---

## Phase 0 — Baseline & safety

**Nothing else ships until this is done.** Two of these are prerequisites; one is unrelated to SEO
but shouldn't wait.

| # | Task | Finding |
|---|---|---|
| 0.1 | **Rotate the City of Winnipeg API token**, `git rm --cached` the properties file, add it to `.gitignore`, commit a `application.properties.example`, and correct the inaccurate claim in `CLAUDE.md`. Rotation matters more than untracking — history keeps the old value. | F03 |
| 0.2 | **Record Search Console baselines** for `wfps.jdsecurity.ca`: position, impressions and CTR for `WFPS`, `winnipeg fire incidents`, `winnipeg fire paramedic`, `wfps incidents`. Export, commit the CSV to `docs/seo/baseline/`. | F22 |
| 0.3 | **Capture a Lighthouse baseline** (mobile) — LCP, CLS, INP, total blocking time. Commit the JSON alongside 0.2. | F05 |
| 0.4 | **Verify `wfps.redspectrum.ca` in Search Console** now, so it has history before the migration. | F01 |
| 0.5 | **Add SRI to the Bootswatch stylesheet** across all four templates, or fold it into the self-hosting work in 3.1. | F04 |

> **Status:** 0.1–0.4 are still outstanding. Phase 1's code tasks shipped ahead of them because
> the live site was serving a canonical pointing at a domain that redirected back to it — a
> conflicting signal worth clearing immediately. **0.2 (Search Console baselines) is now partly
> unrecoverable** for the pre-migration window; capture whatever the 16-month report still holds.

**Exit:** baselines committed, token rotated, both properties verified.

---

## Phase 1 — Domain migration

The highest-risk phase. We currently hold a #1 for `WFPS`; a botched migration loses it.

| # | Task | Finding |
|---|---|---|
| 1.1 | ✅ **Done.** `app.baseUrl` added; `SiteIdentityAdvice` (`@ControllerAdvice`) publishes it to every view. All 9 hardcoded occurrences replaced — canonical, `og:url`, `og:image`, `twitter:url`, `twitter:image`, JSON-LD `url`, robots, sitemap, contact link. | F02 |
| 1.2 | ✅ **Done.** `robots.txt` and `sitemap.xml` are now `SeoController` routes. Static copies deleted. Sitemap also picked up a real `<lastmod>` and dropped the ignored `changefreq`/`priority`, closing F14 early. | F02, F14 |
| 1.3 | ✅ **Done.** Contact halves come from the server via `WFPS_DATA`. **Left pointing at `wfps@jdsecurity.ca` deliberately** — the mailbox is independent of the site domain. Change `app.contactEmail` if it has moved. | F16, F02 |
| 1.4 | ✅ **Done** (infrastructure). `wfps.redspectrum.ca` is serving. | F01 |
| 1.5 | ✅ **Done** (infrastructure). `jdsecurity.ca` 301s to `redspectrum.ca`. **Verify the redirect is path-preserving**, not a blanket redirect to `/`. | F01 |
| 1.6 | File the **Search Console change-of-address** from `jdsecurity.ca` to `redspectrum.ca`. | F01 |
| 1.7 | Update the external links we control: the GitHub repo description and README, and the LinkedIn post that currently links the old domain. | F21 |
| 1.8 | **Keep `jdsecurity.ca` registered and redirecting indefinitely.** Letting it lapse discards every inbound link. | F01 |

**Remaining:** 1.6 (change-of-address filing), 1.7 (update external links), 1.8 (keep the old domain renewed).

**Exit:** `redspectrum.ca` indexed, old URLs 301ing, change-of-address accepted.
**Then wait 2–4 weeks and re-measure against 0.2 before starting Phase 2.**

> **Rollback:** if positions drop sharply and hold for more than ~2 weeks, the 301s are reversible.
> This is the one phase with a real rollback path — use it rather than layering fixes on top.

---

## Phase 2 — On-page foundations

Starts only once Phase 1 has settled and been measured.

| # | Task | Finding |
|---|---|---|
| 2.1 | ✅ **Shipped 2026-08-25.** Title is now `Winnipeg Fire Incidents – Live WFPS Active Incident Map` (55 chars). | F07 |
| 2.2 | ✅ **Shipped.** Description rewritten (148 chars, leads with the target phrase, states the 5-minute cadence). `keywords` dropped and image alt text added earlier. `twitter:site` still omitted — it needs a real account handle. | F07, F18, F19 |
| 2.3 | **Fix the JSON-LD attribution:** `provider`/`author` become the actual operator; WFPS moves to `sourceOrganization`. Add the `Dataset` block — with `temporalCoverage`, `creator`, `distribution` and `license`, and **without** the non-existent `updateFrequency` field. | F09 |
| 2.4 | **Add a visible non-affiliation disclaimer** in the footer: not affiliated with WFPS or the City of Winnipeg; data sourced from the Open Data portal. | F09 |
| 2.5 | **Add prose and heading structure.** `<h2>` sections below the map: how WFPS dispatches, what the categories mean, where the data comes from, what the limitations are (neighbourhood-level only, `Unverified` early records). Written for readers — no keyword padding. Build `/about` as the long-form version. | F08, F10, F17 |
| 2.7 | **Server-render a `<time datetime="…">` last-updated stamp** near the `<h1>`, sourced from the sync timestamp — so freshness is visible to crawlers, not just to `maps.js`. | F11 |
| 2.8 | **Verify error status codes:** `/404` must return HTTP 404, `/500` must return 500, and both templates need `<meta name="robots" content="noindex">`. | F15 |
| 2.9 | **Add the "not an emergency service" notice** — *call 911 in an emergency; this is an unofficial view of published open data*. Ships with the first content change, not later. | F27 |
| 2.10 | **Add an RSS/Atom feed** at `/feed.xml`. Syndication and links without broadcasting live emergencies. | F25 |

**Exit:** one indexable page that is substantially better than it is today, plus `/about`.

> **Measurement note.** 2.1 and 2.2 shipped together on **2026-08-25**, without the Phase 0.2
> baseline — it could not be captured before the migration. Ranking movement from here is therefore
> **not attributable** to either the domain move or the retitle; the two are confounded. Search
> Console still holds the history, so use its date-comparison against **2026-08-25** to eyeball
> before/after by hand. Treat the result as a signal, not a measurement.

---

## Phase 3 — Performance

Independent of the migration; can run alongside Phases 1–2.

| # | Task | Finding |
|---|---|---|
| 3.1 | **Self-host Leaflet CSS/JS and the Bootstrap theme.** Removes two third-party origins from the critical path and retires the SRI problem in 0.5 entirely. | F04, F05 |
| 3.2 | **Defer Leaflet JS** — the map sits below the `<h1>` and does not need to block first paint. | F05 |
| 3.3 | **Inline critical CSS** for the header and the table's first rows; load the rest async. | F05 |
| 3.4 | **Defer `gtag.js`** below the fold. | F06 |
| 3.5 | Add far-future `Cache-Control` for `/scripts/`, `/css/` and static images; enable response compression. | F05 |
| 3.6 | **Stop fetching upstream inside a request.** `AppController` currently calls `syncIncidentsTableSafe()` inline on an empty result, so during an API outage every page load blocks on a failing HTTP call. Serve stale data, add a circuit breaker, and cache the rendered model briefly. **Crawl-health issue, not just latency.** | F24 |
| 3.7 | Emit `Last-Modified`/`ETag` on `/` from the last sync time. | F30 |
| 3.8 | **Re-run Lighthouse and diff against 0.3.** Target: LCP < 2.5s, CLS < 0.1 on mobile. | F05 |

**Exit:** all three Core Web Vitals in the green on mobile.

---

## Phase 4 — Incident history

Pure infrastructure. No SEO output on its own — it exists to make Phase 5 safe.

| # | Task | Finding |
|---|---|---|
| 4.1 | Add an **append-only `incident_history` table**. `Database.syncIncidentsTable()` upserts into it rather than deleting; the existing `incidents` table and its 24-hour window are left exactly as they are. | F12 |
| 4.2 | Derby is embedded and file-backed — **decide the retention ceiling and the backup story now**, before the table grows. Consider whether this is the point to move off Derby. | F12 |
| 4.3 | Add aggregate queries: calls per ward, per neighbourhood, per type, per period; median on-scene duration. These are what give Phase 5 pages something to say. | F12 |
| 4.4 | **Backfill history** from the Open Data portal — the dataset goes back to 2015. A one-shot import turns day-one pages from empty into substantive. | F12 |
| 4.5 | Tests for the aggregates and for retention behaviour. | F12 |

**Exit:** queryable history with enough depth that a ward page has real content on day one.

---

## Phase 5 — Programmatic pages

**Do not start before Phase 4 is complete and backfilled.** The failure mode here is site-level, not
page-level: generated pages with nothing on them are thin content at scale.

| # | Task | Finding |
|---|---|---|
| 5.1 | **Define the content template first.** Every generated page must carry live incidents *plus* historical stats *plus* unique prose — enough to stand alone with zero active incidents. If the template can't clear that bar, stop; the whole phase is unsafe. | F13 |
| 5.2 | **Ward pages first — 15 of them.** `/ward/{slug}` for the entries in `wardCentres`. Bounded, each with real volume. | F13 |
| 5.3 | **Incident-type pages.** `/incident-type/{slug}` — fire rescue, medical response, motor vehicle. Clean entity slugs, not `motor-vehicle-incident-winnipeg`. | F13 |
| 5.4 | **Internal linking:** map and table entries link to their ward and type pages; each generated page links back to `/` and to sibling entities. This is what makes them crawlable *and* what fixes F17. | F17, F13 |
| 5.5 | **Neighbourhood pages, gated.** Only for neighbourhoods clearing a call-volume threshold over the history window. Not all 237. Re-evaluate the threshold quarterly. | F13 |
| 5.6 | Per-page canonical, title, description and JSON-LD (`Place` / `Dataset` as appropriate). Extend the generated sitemap to cover them with per-page `lastmod`. | F13, F14 |
| 5.7 | **Watch Search Console coverage weekly for the first month.** Rising "Crawled – currently not indexed" means the pages read as thin. If that happens, **cut page count rather than adding more**. | F13 |

**Exit:** 20–40 genuinely substantive indexed pages. Quality bar over page count, every time.

---

## Phase 5A — The archive (the source-of-record play)

**This is the highest-value phase on the roadmap.** Rationale in `SEOPlan.md` §3: `winnipeg.ca` shows
only ongoing incidents, so the entire retrospective query space is unoccupied. Runs immediately after
Phase 4 and takes precedence over Phase 5's live entity pages where the two compete for time.

| # | Task | Finding |
|---|---|---|
| 5A.1 | **Daily archive pages** — `/incidents/{yyyy-mm-dd}`: every call that day, counts by category and ward, notable incidents by duration or units dispatched. This is what answers *"what was that fire last night?"* | F23 |
| 5A.2 | **Pull the ward statistics dataset** (`jq3f-ckpm`) so ward pages carry real historical volume on day one rather than waiting on our own backfill. | F28 |
| 5A.3 | **Per-area history sections** on ward and neighbourhood pages: recent calls, volume trend, typical response mix. | F23 |
| 5A.4 | **Explanatory content** for the "why so many sirens tonight" class of query — how dispatch escalates, why several units attend one call, what the categories mean in practice. | F23, F10 |
| 5A.5 | **`BreadcrumbList` JSON-LD** across the archive hierarchy. | F26 |
| 5A.6 | **Per-page OG images** — a generated static map thumbnail per ward and date page. | F29 |
| 5A.7 | **Guard the volume.** Daily pages are ~365/year. Ship a rolling window first and only expand once the template demonstrably clears the substance bar in 5.1. | F23, F13 |

**Exit:** we answer a question no other site answers, on a URL per day and per area.

---

## Phase 6 — Authority & measurement

Ongoing, starts once there is something worth linking to (post-Phase 2 at the earliest).

| # | Task | Finding |
|---|---|---|
| 6.1 | **Weekly aggregate summary pages** — "WFPS call volume, week of X". The linkable, compounding substitute for the auto-posting bot we rejected. | F20 |
| 6.1b | **Annual "Winnipeg fire and EMS: year in data" report** with charts. The realistic path to local-journalist citations, which are the only thing that closes an authority gap with a `.ca` domain. | F32 |
| 6.2b | **IndexNow** for Bing/Yandex. Google does not support it, and its Indexing API is `JobPosting`/`BroadcastEvent` only — not a shortcut available to us. | F31 |
| 6.2 | **Manual outreach:** r/Winnipeg, Winnipeg civic-tech and open-data communities, the City's open-data showcase if it accepts submissions, local news tips. Human posts, not automation. | F21 |
| 6.3 | Cross-link from our own properties: GitHub profile and repo, LinkedIn, any other owned domains. | F21 |
| 6.4 | **Monthly ranking review** against the 0.2 baseline for the four target queries. | F22 |
| 6.5 | Re-run Lighthouse quarterly — performance regresses silently. | F05 |

**Explicitly out of scope:** the automated per-incident social posting bot from the source roadmap.
Rationale in `SEOPlan.md` §G5.

---

## What success looks like

| Horizon | Target |
|---|---|
| **Migration complete** | `redspectrum.ca` holds the `WFPS` position `jdsecurity.ca` held. Migration is a success when nothing gets worse. |
| **~3 months** | Page 1 for `winnipeg fire incidents`. Core Web Vitals green. 20+ indexed pages. |
| **~6 months** | Competitive with `winnipegfire.live` on its own keywords; ranking for long-tail ward and type queries neither competitor covers. |
| **Stretch** | Above `winnipeg.ca` for `winnipeg fire incidents`. Honest read: this needs real inbound links, and it is a 12-month goal, not a 6-month one. |

Better framing than that stretch goal: **we do not need to outrank `winnipeg.ca` on its own query to
win.** Ranking first for the retrospective and statistical queries it does not serve is a larger
addressable audience than second place on *"winnipeg fire incidents"*, and it is winnable this year.

## Open questions

1. **Analytics continuity** — does `G-FGQZ0FFFV1` follow to the new domain, or is this the moment for a fresh property? Affects whether pre/post comparison is possible in GA.
2. **Named author for E-E-A-T** — the disclaimer and `/about` page work best with a real name and contact attached. Comfortable with that, or keep it project-branded?
3. **Derby's ceiling** (task 4.2) — how many years of history do we want to hold, and does that answer force a database change?
4. **Refresh cadence** — our 5 minutes is *identical to `winnipeg.ca`* and `winnipegfire.live` runs at 30s, making us the slowest of the three on live incidents. Is a tighter cadence viable within the Open Data API's rate limits, or do we concede the live query and commit to the archive? (F33)
5. **Retention ceiling for the archive** — how far back do daily pages go? This is the same question as 4.2, but the answer now has an SEO consequence rather than only a storage one.
