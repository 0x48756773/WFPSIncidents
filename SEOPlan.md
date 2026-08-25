# WFPS Incident Map — SEO Plan

Status: draft for review · Branch: `claude/wfps-seo-strategy-31om1q`

This document does two things:

1. Reviews the SEO roadmap supplied by Google Gemini, item by item.
2. Records the full findings register for this codebase — what to change and why.

The execution sequence lives in [`SEOImplementationPlan.md`](SEOImplementationPlan.md).

---

## 1. Where we actually stand

| | |
|---|---|
| Indexed domain | `wfps.jdsecurity.ca` — ranks for `WFPS` queries |
| Target domain | `wfps.redspectrum.ca` — **zero index presence today** |
| Stack | Spring Boot 3 + Thymeleaf, server-rendered. Not an SPA. |
| Indexable pages | **1** (`/`) |
| Data retention | 24h, wiped and rebuilt on every 5-min sync (`DELETE FROM incidents`) |
| Refresh cadence | 5 minutes |
| Structured data | `WebApplication` JSON-LD (contains a factual error — see F09) |
| Third-party render-blocking resources in `<head>` | 3 |

**Competitive read.** `winnipegfire.live` outranks us for *"Winnipeg fire incidents"*. Gemini attributes
this to an Exact Match Domain advantage. That is very likely the wrong diagnosis, and acting on it
would send us after the wrong fix. Google's 2012 EMD update specifically demoted thin exact-match
domains; exact match is a weak signal today. The likelier causes, all of which we can act on:

- **They refresh every 30 seconds and say so.** We refresh every 5 minutes and don't surface it to crawlers.
- **They have more than one indexable page.** `winnipegfire.live/dispatch/` is separately indexed. We have one URL.
- **They read as a purpose-built brand.** Ours reads as a subdomain of an unrelated parent domain — and is about to change again.

Beating `winnipeg.ca` is a separate and harder problem: it is a government domain with authority we
cannot match on-page. We beat it on *coverage and speed* (pages for slices of the data it has no page
for) or not at all. That is the one strategic point Gemini gets exactly right.

---

## 2. Review of the Gemini roadmap

Verdict key: ✅ adopt · ⚠️ adopt with corrections · ❌ reject

### ✅⚠️ G1 — Re-target metadata

**Adopt the direction, reject the specific title.**

Gemini's proposal — `Winnipeg Fire Incidents & WFPS Dispatch Map | Real-Time` — drops
"Winnipeg Fire Paramedic Service", which is the exact phrase currently earning our #1 for WFPS
queries. That trades a ranking we hold for one we might win. `| Real-Time` also burns tail
characters on a phrase nobody searches.

Counter-proposal, leading with the target phrase while keeping the brand string intact:

```
Winnipeg Fire Incidents – Live WFPS Active Incident Map
```

55 characters, leads with the contested keyword, retains `WFPS`, `Incident`, `Map`, `Live`.

The meta description rewrite is fine and worth taking largely as written.

**Sequencing caveat Gemini could not have known:** we are mid-domain-migration. Changing titles and
changing domains simultaneously makes any ranking movement unattributable. Metadata changes wait
until the migration has settled.

### ⚠️ G2 — Programmatic SEO

**The strongest idea in the roadmap, and the one most likely to damage the site if built as written.**

Right on strategy: `winnipeg.ca` has one monolithic incident page. Generating pages for slices of the
data is how a small site out-covers a large one.

Four corrections:

1. **We have no history to build these pages from.** `Database.syncIncidentsTable()` runs
   `DELETE FROM incidents` on every 5-minute sync and retains a 24-hour window. A
   `/neighbourhood/wolseley` page would be empty most of the time. 237 usually-empty pages is a
   textbook thin-content / soft-404 pattern, and Google's spam policies name scaled content
   abuse explicitly. Built naively this risks the *whole domain*, not just the new URLs.
   **An incident history table is a hard prerequisite** (F12), not an optimisation.
2. **Start with 15 wards, not 237 neighbourhoods.** Wards are bounded and each carries real call
   volume. Neighbourhoods come later, and only for those clearing a volume threshold.
3. **Gemini's own example is wrong for our data.** It suggests
   `/neighbourhood/point-douglas-fire-incidents` — but Point Douglas is a **ward** in this dataset
   (`wardCentres` in `maps.js`), not a neighbourhood. The slug is also keyword-stuffed. Use clean
   entity URLs: `/ward/point-douglas`, `/incident-type/motor-vehicle`.
4. `/type/motor-vehicle-incident-winnipeg` **is** feasible — the feed carries a
   `motor_vehicle_incident` field we already surface in the table.

### ⚠️ G3 — Inject semantic text content

**Right recommendation, wrong premise, one bad specific.**

The premise ("SPAs often struggle... the body is mostly `<div id="root">`") does not apply to us at
all — see G6. But the recommendation stands on its own merits: the page currently has one `<h1>`,
one subtitle paragraph, and then a data table. No heading hierarchy, no explanatory prose.

**Reject** the instruction to work in phrases like *"Winnipeg emergency pulse"*. That is not a phrase
anyone types into a search box; it is keyword padding, and it reads as such to both users and
classifiers. Write for readers.

The version worth building is an About / Methodology section plus a dedicated `/about` page: data
provenance, refresh cadence, what "active" means, category definitions, and honest limitations
(neighbourhood-level only, never addresses; early records may be `Unverified`). That doubles as
E-E-A-T, which matters more than usual here — we are a private site publishing public-safety
information in a space owned by a government incumbent.

### ⚠️ G4 — JSON-LD `Dataset` structured data

**Worth doing. Both technical specifics are wrong, and the payoff is oversold.**

- `updateFrequency` **is not a Schema.org `Dataset` property.** No such field exists. Update cadence
  is conventionally expressed with `dcterms:accrualPeriodicity`; Schema.org's `repeatFrequency`
  belongs to `Schedule`, not `Dataset`.
- *"often granting them special rich snippets in search results"* — **no.** `Dataset` markup feeds
  **Google Dataset Search**, a separate vertical. It produces no rich snippet in ordinary web
  results. Still worth adding — it is cheap and Dataset Search is a genuine discovery channel for an
  open-data site — but not for the reason given.

While we are in this block, there is a **pre-existing error Gemini did not catch** (F09): our current
JSON-LD declares `"provider": { "@type": "Organization", "name": "Winnipeg Fire Paramedic Service" }`.
That asserts WFPS provides this application. It does not. Beyond being structurally wrong, it implies
an affiliation with a public agency that we do not have.

### ✅❌ G5 — Backlinks and social signals

**The backlink half is correct and is our highest-leverage work. The automated-posting half should not be built.**

Correct: the authority gap is the main reason `winnipeg.ca` wins, and links close it.

**Reject the "automatically post major events to X/Mastodon" bot**, on three grounds:

1. **It doesn't do what's claimed.** Social links are `nofollow`/`ugc` and pass no authority.
   "Social signals drive rapid indexing" is not a mechanism Google endorses.
2. **It gets the account killed.** An account firing automated posts about live emergencies is a
   spam-filter target.
3. **The real objection:** auto-broadcasting active incidents in near-real-time can draw onlookers
   to live emergency scenes, and it amplifies early data that the feed itself flags as unreliable —
   the `Unverified` neighbourhood value exists precisely because early records can't be trusted.

Substitute, which is both safer and better SEO: a **weekly aggregate summary page** ("WFPS call
volume, week of X") — genuinely linkable, genuinely useful, and it compounds. Plus manual outreach:
r/Winnipeg, Winnipeg civic-tech and open-data communities, local news tips. One local news pickup
outweighs a year of automated posts.

### ❌ G6 — "Ensure Server-Side Rendering"

**Already done. This is the item that shows the roadmap was written without looking at the site.**

This is Spring Boot + Thymeleaf. `AppController` renders `index.html` server-side and the **complete
incident table — neighbourhoods, types, timestamps, wards — ships in the initial HTML response**
before any JavaScript executes. `maps.js` only enhances what's already there. There is no
`<div id="root">`, no client-side routing, and no React or Vite anywhere in the project.

The actual rendering problem is the inverse: **we block our own first paint with third-party
resources** (F05). Core Web Vitals are a real ranking input, and speed is precisely where a modern
web app should be beating a government portal.

---

## 3. Findings register

Severity: **P0** ship first · **P1** high value · **P2** worthwhile · **P3** housekeeping

### Migration & integrity

| ID | Finding | Sev | Effort |
|---|---|---|---|
| **F01** | **Domain migration to `wfps.redspectrum.ca`.** All ranking equity sits on `jdsecurity.ca`. Needs 301s from every old URL, canonical/OG/sitemap rewrite, a Search Console change-of-address, and the old domain kept alive and redirecting indefinitely. Get this wrong and the WFPS #1 is gone. | P0 | M |
| **F02** | **Domain hardcoded in 9 places** across `index.html`, `sitemap.xml`, `robots.txt`, `maps.js`. Guarantees drift. Extract to one `app.baseUrl` property and template every reference from it. | P0 | S |
| **F03** | **API token committed to a public repo.** `application.properties` is git-tracked with a live `secret.cityOfWinnipeg` value, contradicting `CLAUDE.md`. *Not an SEO issue — flagged because it was found. Rotate the token and untrack the file.* | P0 | S |
| **F04** | **Bootstrap CSS loaded from `bootswatch.com` with no SRI hash**, on all four templates. The Leaflet tags carry `integrity`; this one doesn't. A third-party stylesheet that can change under us on every page load. | P0 | S |

### Performance (Core Web Vitals)

| ID | Finding | Sev | Effort |
|---|---|---|---|
| **F05** | **Three render-blocking third-party resources in `<head>`**: Leaflet CSS + Leaflet JS (unpkg) and Bootstrap CSS (bootswatch). Self-host, and defer Leaflet JS — the map is below the `<h1>`. | P1 | M |
| **F06** | **`gtag.js` loads before page content.** Move analytics below the fold or defer it. | P2 | S |

### On-page

| ID | Finding | Sev | Effort |
|---|---|---|---|
| **F07** | **Title doesn't lead with the contested phrase.** Adopt `Winnipeg Fire Incidents – Live WFPS Active Incident Map`. *Ships after F01 settles.* | P1 | S |
| **F08** | **No heading hierarchy.** One `<h1>`, then a `<summary>`. No `<h2>`/`<h3>` anywhere. | P1 | S |
| **F09** | **JSON-LD misattributes the app to WFPS.** `provider` names Winnipeg Fire Paramedic Service. Set `provider`/`author` to the actual operator, move WFPS to `sourceOrganization`, and add a visible non-affiliation disclaimer. | P1 | S |
| **F10** | **No explanatory prose and no `/about` page.** E-E-A-T gap against a government incumbent on a public-safety topic. | P1 | M |
| **F11** | **Freshness invisible to crawlers.** `#refresh-badge` is populated client-side. Server-render a `<time datetime="…">` last-updated stamp. Our competitor advertises 30-second refresh; we advertise nothing. | P1 | S |
| **F16** | **Contact email exists only in JS** (`maps.js:823`), so crawlers never see it — and it hardcodes `jdsecurity.ca`, which F01 breaks. | P2 | S |
| **F17** | **Zero internal links.** The only link out of the page body goes to GitHub. No crawl paths, no link equity distribution. Blocks F13. | P2 | S |
| **F18** | **Missing `og:image:alt` and `twitter:site`.** | P3 | S |
| **F19** | **`<meta name="keywords">`** — ignored by Google since 2009. Remove. | P3 | S |

### Indexation

| ID | Finding | Sev | Effort |
|---|---|---|---|
| **F14** | **Sitemap is static and misconfigured.** `changefreq` and `priority` are ignored by Google; `<lastmod>`, which *is* used, is absent. Generate the sitemap at runtime with an honest `lastmod`. | P1 | S |
| **F15** | **Error page status codes unverified.** Confirm `/404` returns HTTP 404 (not 200) and that error templates carry `noindex`. A soft-404 farm is a real risk once F13 adds routes. | P2 | S |

### Content expansion

| ID | Finding | Sev | Effort |
|---|---|---|---|
| **F12** | **No incident history.** 24h retention, wiped every sync. **Hard prerequisite for F13.** Add an append-only history table; the live table keeps its current behaviour. | P1 | L |
| **F13** | **Only one indexable URL.** Programmatic pages for wards → incident types → high-volume neighbourhoods. **Gated on F12 and F17.** | P1 | L |
| **F20** | **No linkable evergreen content.** Weekly aggregate summary pages (the safe substitute for G5's posting bot). Gated on F12. | P2 | M |

### Off-page

| ID | Finding | Sev | Effort |
|---|---|---|---|
| **F21** | **Authority gap.** Manual outreach: r/Winnipeg, civic-tech and open-data communities, local news. No automated posting (see G5). | P1 | ongoing |
| **F22** | **No rank tracking.** We cannot tell whether any of this worked. Baseline Search Console positions for the target queries *before* F01 ships. | P0 | S |

---

## 4. Two things that must not happen

1. **Do not ship F01 (migration) and F07 (title rewrite) together.** If rankings move, we won't know
   which caused it. Migrate, let it settle, then touch metadata.
2. **Do not ship F13 (programmatic pages) before F12 (history).** Empty generated pages are worse
   than no pages — that is site-level risk, not page-level.
