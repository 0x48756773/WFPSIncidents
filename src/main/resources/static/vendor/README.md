# Vendored front-end assets

Third-party assets served from this origin rather than a CDN. Both were previously
loaded from external hosts; see `SEOPlan.md` (F04/F05) for why they moved.

| Asset | Version | Upstream |
|---|---|---|
| `leaflet/` | **1.9.4** | https://www.npmjs.com/package/leaflet |
| `bootstrap/bootstrap.min.css` | **bootswatch 5.3.8** (Minty) | https://www.npmjs.com/package/bootswatch |

## Updating

    npm view leaflet version
    npm view bootswatch version

If newer, fetch and copy the same files, then verify the map and table render
in both light and dark mode before committing.

    npm i leaflet@<version> bootswatch@<version>
    cp node_modules/leaflet/dist/leaflet.{js,css}          .../vendor/leaflet/
    cp node_modules/leaflet/dist/images/*.png              .../vendor/leaflet/images/
    cp node_modules/bootswatch/dist/minty/bootstrap.min.css .../vendor/bootstrap/

Realistic cadence: Leaflet 1.9.4 shipped in May 2023 and is still current, so check
it yearly. Bootswatch ships 2-4 cosmetic 5.3.x patches a year — twice a year is fine.

Note the previous Bootstrap URL (`bootswatch.com/5/minty/bootstrap.css`) was an
unversioned major-version path, so the site was silently taking every upstream
release without review. That is why SRI was never an option there: pinning a hash
to a URL whose content changes turns the next upstream patch into an outage.

`images/` holds Leaflet's default marker icons. Nothing currently requests them —
the map uses `L.circleMarker`, which is drawn as SVG — but they are kept so a future
`L.marker()` does not fail on a missing file.
