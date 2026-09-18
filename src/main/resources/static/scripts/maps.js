/*
 * WFPS Active Incident Map — client-side logic.
 *
 * Incident data is injected server-side via a small inline <script> that sets
 * window.WFPS_DATA. This file holds all map/table behaviour and is fully static,
 * so the browser can cache it between refreshes.
 */
(function () {
    'use strict';

    // Replaced wholesale by each refresh rather than reloaded with the page, so this is
    // the current list, not the one the page was built with.
    let incidents = (window.WFPS_DATA && window.WFPS_DATA.incidents) || [];

    /**
     * Analytics. Every interesting interaction on this page — choosing a filter, opening an
     * incident, installing the app — happened entirely in the browser and was therefore
     * invisible: the only events being recorded were page views and outbound link clicks, so
     * there was no way to tell whether any of this was used.
     *
     * No-ops when the tag is absent, which is the case locally, under test, and for anyone
     * running an ad blocker. Nothing here may depend on it having run.
     */
    function track(name, params) {
        if (typeof window.gtag === 'function') {
            try {
                window.gtag('event', name, params || {});
            } catch (error) {
                /* Analytics must never break the map. */
            }
        }
    }

    // Everything below used to assume Leaflet had loaded. It is one script from one host,
    // and this file is a single IIFE: if L was missing, line one threw and took the whole
    // page's behaviour with it — filters, dark mode, table interaction, the contact link.
    // The map is now optional. When it is unavailable the table still works.
    const leafletReady = typeof L !== 'undefined' && typeof L.map === 'function';

    const map = leafletReady ? L.map('map').setView([49.8951, -97.1384], 11) : null;
    const lightTile = leafletReady ? L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }) : null;
    const darkTile = leafletReady ? L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
    }) : null;
    if (map) {
        lightTile.addTo(map);
    } else {
        const panel = document.getElementById('map');
        if (panel) {
            panel.classList.add('map-panel-unavailable');
            panel.textContent = 'The map could not be loaded. The incident table below is still up to date.';
        }
    }

    const neighbourhoodCentres = {
        'Agassiz': [49.8168, -97.1428],
        'Airport': [49.9133, -97.2471],
        'Alpine Place': [49.8528, -97.0946],
        'Amber Trails': [49.9704, -97.1794],
        'Archwood': [49.8770, -97.1030],
        'Armstrong Point': [49.8772, -97.1549],
        'Assiniboia Downs': [49.8766, -97.3298],
        'Assiniboine Park': [49.8689, -97.2451],
        'Beaumont': [49.8471, -97.1660],
        'Betsworth': [49.8580, -97.3022],
        'Birchwood': [49.8757, -97.2662],
        'Booth': [49.8880, -97.2680],
        'Bridgwater Centre': [49.7960, -97.1958],
        'Bridgwater Forest': [49.8082, -97.1840],
        'Bridgwater Lakes': [49.8030, -97.2010],
        'Bridgwater Trails': [49.7920, -97.2020],
        'Broadway-Assiniboine': [49.8860, -97.1390],
        'Brockville': [49.8459, -97.1930],
        'Brooklands': [49.9211, -97.1971],
        'Bruce Park': [49.8750, -97.2210],
        'Buchanan': [49.8931, -97.3198],
        'Buffalo': [49.8364, -97.1775],
        'Burrows Central': [49.9280, -97.1580],
        'Burrows-Keewatin': [49.9227, -97.1911],
        'Canterbury Park': [49.8975, -96.9750],
        'Central Park': [49.8960, -97.1510],
        'Central River Heights': [49.8598, -97.1815],
        'Central St. Boniface': [49.8914, -97.1051],
        'Centennial': [49.9047, -97.1433],
        'Chalmers': [49.9049, -97.1045],
        'Chevrier': [49.8350, -97.1650],
        'China Town': [49.9043, -97.1389],
        'Civic Centre': [49.8963, -97.1368],
        'Cloutier Drive': [49.7810, -97.1430],
        'Colony': [49.8916, -97.1521],
        'Crescent Park': [49.8348, -97.1444],
        'Crescentwood': [49.8680, -97.1700],
        'Crestview': [49.8994, -97.2967],
        'Dakota Crossing': [49.8190, -97.0930],
        'Daniel McIntyre': [49.9040, -97.1631],
        'Deer Lodge': [49.8869, -97.2320],
        'Dufferin': [49.9180, -97.1545],
        'Dufferin Industrial': [49.9130, -97.1560],
        'Dufresne': [49.8822, -97.1028],
        'Dugald': [49.8920, -97.0640],
        'Eaglemere': [49.9159, -97.0559],
        'Earl Grey': [49.8693, -97.1518],
        'East Elmwood': [49.9045, -97.0842],
        'Ebby-Wentworth': [49.8610, -97.1480],
        'Edgeland': [49.8689, -97.2086],
        'Elm Park': [49.8610, -97.1119],
        'Elmhurst': [49.8525, -97.2555],
        'Eric Coy': [49.8577, -97.2756],
        'Exchange District': [49.8980, -97.1366],
        'Fairfield Park': [49.8010, -97.1610],
        'Fort Richmond': [49.7954, -97.1179],
        'Fraipont': [49.8265, -97.0570],
        'Garden City': [49.9410, -97.1500],
        'Glendale': [49.8775, -97.3202],
        'Glenelm': [49.9188, -97.1155],
        'Glenwood': [49.8638, -97.0975],
        'Grant Park': [49.8594, -97.1651],
        'Grassie': [49.9152, -97.0557],
        'Griffin': [49.9139, -96.9950],
        'Heritage Park': [49.8915, -97.2885],
        'Holden': [49.8859, -97.0735],
        'Inkster Gardens': [49.9515, -97.1934],
        'Inkster Industrial Park': [49.9435, -97.1690],
        'Inkster-Faraday': [49.9320, -97.1480],
        'Island Lakes': [49.8250, -97.0550],
        'J. B. Mitchell': [49.8640, -97.1977],
        'Jameswood': [49.8881, -97.2515],
        'Jefferson': [49.9349, -97.1233],
        'Kensington': [49.8850, -97.2050],
        'Kern Park': [49.9020, -96.9970],
        'Kil-Cona Park': [49.9383, -97.0167],
        'Kildare-Redonda': [49.9045, -96.9850],
        'Kildonan Crossing': [49.9075, -97.0641],
        'Kildonan Drive': [49.9379, -97.1082],
        'Kildonan Park': [49.9476, -97.1025],
        'King Edward': [49.8872, -97.2162],
        'Kingston Crescent': [49.8516, -97.1208],
        'Kirkfield': [49.8705, -97.2810],
        'La Barriere': [49.7460, -97.1920],
        'Lavalee': [49.8456, -97.0916],
        'Legislature': [49.8836, -97.1509],
        'Leila North': [49.9660, -97.1548],
        'Leila-McPhillips Triangle': [49.9469, -97.1594],
        'Linden Ridge': [49.8220, -97.1840],
        'Linden Woods': [49.8295, -97.1919],
        'Logan-C.P.R.': [49.9050, -97.1550],
        'Lord Roberts': [49.8550, -97.1475],
        'Lord Selkirk Park': [49.9106, -97.1356],
        'Luxton': [49.9227, -97.1178],
        'Maginot': [49.8741, -97.0806],
        'Mandalay West': [49.9516, -97.1825],
        'Maple Grove Park': [49.7916, -97.1268],
        'Margaret Park': [49.9487, -97.1198],
        'Marlton': [49.8653, -97.2708],
        'Mathers': [49.8560, -97.2010],
        'Maybank': [49.8352, -97.1583],
        'McLeod Industrial': [49.9305, -97.0660],
        'McMillan': [49.8758, -97.1524],
        'Meadowood': [49.8310, -97.0850],
        'Meadows': [49.9014, -97.0295],
        'Melrose': [49.8941, -97.0053],
        'Minnetonka': [49.8140, -97.1250],
        'Minto': [49.8892, -97.1795],
        'Mission Gardens': [49.8890, -97.0310],
        'Mission Industrial': [49.8949, -97.1004],
        'Montcalm': [49.8145, -97.1547],
        'Munroe East': [49.9097, -97.0769],
        'Munroe West': [49.9207, -97.0946],
        'Murray Industrial Park': [49.8980, -97.2600],
        'Mynarski': [49.9407, -97.1599],
        'Niakwa Park': [49.8650, -97.0960],
        'Niakwa Place': [49.8498, -97.0884],
        'Norberry': [49.8493, -97.1153],
        'Normand Park': [49.8069, -97.1197],
        'North Inkster Industrial': [49.9534, -97.2184],
        'North Point Douglas': [49.9036, -97.1088],
        'North River Heights': [49.8720, -97.1850],
        'North St. Boniface': [49.9015, -97.1229],
        'North Transcona Yards': [49.9299, -97.0249],
        'Norwood East': [49.8750, -97.1070],
        'Norwood West': [49.8746, -97.1320],
        'Oak Point Highway': [49.9397, -97.2160],
        'Old Tuxedo': [49.8732, -97.2079],
        "Omand's Creek Industrial": [49.9233, -97.2155],
        'Pacific Industrial': [49.9089, -97.1732],
        'Parc La Salle': [49.7685, -97.1600],
        'Parker': [49.8542, -97.1619],
        'Peguis': [49.9045, -97.0520],
        'Pembina Strip': [49.8233, -97.1527],
        'Perrault': [49.7475, -97.1570],
        'Point Road': [49.8548, -97.1465],
        'Polo Park': [49.8822, -97.2035],
        'Portage & Main': [49.8947, -97.1392],
        'Portage-Ellice': [49.8917, -97.1509],
        'Prairie Pointe': [49.7753, -97.1992],
        'Pulberry': [49.8315, -97.1300],
        'Radisson': [49.9039, -97.0108],
        'Regent': [49.8990, -97.0590],
        'Richmond Lakes': [49.7689, -97.1652],
        'Richmond West': [49.7866, -97.1673],
        'Ridgedale': [49.8690, -97.2760],
        'Ridgewood South': [49.8456, -97.2735],
        'River East': [49.9480, -97.0550],
        'River Park South': [49.8090, -97.1164],
        'River West Park': [49.8710, -97.3190],
        'River-Osborne': [49.8804, -97.1398],
        'Riverbend': [49.9649, -97.0950],
        'Rivergrove': [49.9570, -97.0800],
        'Riverview': [49.8730, -97.1340],
        'Robertson': [49.9389, -97.1637],
        'Roblin Park': [49.8578, -97.2935],
        'Rockwood': [49.8590, -97.1700],
        'Roslyn': [49.8789, -97.1565],
        'Rosser-Old Kildonan': [49.9749, -97.1550],
        'Rossmere-A': [49.9345, -97.0769],
        'Rossmere-B': [49.9255, -97.0799],
        'Royalwood': [49.8280, -97.0810],
        'Sage Creek': [49.8249, -97.0335],
        'Sargent Park': [49.9040, -97.1870],
        'Saskatchewan North': [49.9120, -97.3200],
        'Seven Oaks': [49.9340, -97.1165],
        'Shaughnessy Park': [49.9240, -97.1820],
        'Silver Heights': [49.8806, -97.2570],
        'Sir John Franklin': [49.8779, -97.1982],
        'South Point Douglas': [49.9018, -97.1268],
        'South Portage': [49.8952, -97.1429],
        'South Pointe': [49.7753, -97.1847],
        'South River Heights': [49.8557, -97.1855],
        'South Tuxedo': [49.8567, -97.2108],
        'Southboine': [49.8640, -97.3010],
        'Southdale': [49.8494, -97.0576],
        'Southland Park': [49.8499, -97.0339],
        'Spence': [49.8950, -97.1570],
        'Springfield North': [49.9383, -97.0495],
        'Springfield South': [49.9269, -97.0537],
        'St. Boniface Industrial Park': [49.8750, -97.0350],
        'St. George': [49.8493, -97.1044],
        'St. James Industrial': [49.8997, -97.2168],
        "St. John's": [49.9200, -97.1330],
        "St. John's Park": [49.9165, -97.1230],
        'St. Matthews': [49.8860, -97.1630],
        'St. Norbert': [49.7706, -97.1533],
        'St. Vital Centre': [49.8247, -97.1106],
        'St. Vital Perimeter South': [49.7813, -97.0796],
        'Stock Yards': [49.8744, -97.0848],
        'Sturgeon Creek': [49.8914, -97.2851],
        'Symington Yards': [49.8647, -97.0380],
        'Talbot-Grey': [49.9046, -97.1010],
        'Templeton-Sinclair': [49.9480, -97.1330],
        'The Forks': [49.8871, -97.1349],
        'The Maples': [49.9469, -97.1720],
        'The Mint': [49.8478, -97.0564],
        'Tissot': [49.8941, -97.1065],
        'Trappistes': [49.7510, -97.1707],
        'Transcona North': [49.9120, -96.9945],
        'Transcona South': [49.8750, -96.9950],
        'Transcona Yards': [49.8859, -96.9881],
        'Turnbull Drive': [49.7541, -97.1364],
        'Tuxedo': [49.8657, -97.2101],
        'Tuxedo Industrial': [49.8467, -97.1968],
        'Tyndall Park': [49.9402, -97.2046],
        'Tyne-Tees': [49.9031, -97.0749],
        'University': [49.8100, -97.1400],
        'Valhalla': [49.9530, -97.0760],
        'Valley Gardens': [49.9164, -97.0591],
        'Varennes': [49.8553, -97.1078],
        'Varsity View': [49.8698, -97.2578],
        'Vialoux': [49.8710, -97.2492],
        'Victoria Crescent': [49.8398, -97.1241],
        'Victoria West': [49.9037, -97.0093],
        'Vista': [49.8250, -97.1080],
        'Waverley Heights': [49.8147, -97.1613],
        'Waverley West B': [49.7949, -97.1878],
        'Wellington Crescent': [49.8790, -97.1850],
        'West Alexander': [49.9057, -97.1599],
        'West Broadway': [49.8831, -97.1550],
        'West Fort Garry Industrial': [49.7720, -97.1850],
        'West Kildonan Industrial': [49.9735, -97.1191],
        'West Perimeter South': [49.8331, -97.3339],
        'West Wolseley': [49.8779, -97.2016],
        'Westdale': [49.8595, -97.3207],
        'Weston': [49.9162, -97.1806],
        'Weston Shops': [49.9220, -97.1700],
        'Westwood': [49.8755, -97.2951],
        'Whyte Ridge': [49.8091, -97.2106],
        'Wildwood': [49.8479, -97.1226],
        'William Whyte': [49.9182, -97.1465],
        'Wilkes South': [49.8452, -97.3090],
        'Windsor Park': [49.8583, -97.0657],
        'Wolseley': [49.8788, -97.1872],
        'Woodhaven': [49.8775, -97.2710],
        'Worthington': [49.8362, -97.1043],
    };

    const neighbourhoodCoordinatesCache = {};
    const geocodeFailures = new Set();

    // The feed uses these in place of a neighbourhood when it has no usable location.
    // They are not places, so they must never be geocoded or plotted — guessing a
    // position for them would invent an incident location that does not exist.
    const NON_LOCATABLE_NEIGHBOURHOODS = new Set(['outside winnipeg', 'unverified']);

    function isLocatable(neighbourhoodName) {
        return !!neighbourhoodName
            && !NON_LOCATABLE_NEIGHBOURHOODS.has(String(neighbourhoodName).trim().toLowerCase());
    }

    const wardCentres = {
        'Assiniboia': [49.8573, -97.2835],
        'Charleswood - Tuxedo - Westwood': [49.8569, -97.2525],
        'Daniel McIntyre': [49.9022, -97.1687],
        'Elmwood - East Kildonan': [49.9307, -97.0708],
        'Fort Garry': [49.8232, -97.1520],
        'Mynarski': [49.9290, -97.1483],
        'North Kildonan': [49.9545, -97.0671],
        'Old Kildonan': [49.9760, -97.1128],
        'Point Douglas': [49.9077, -97.1150],
        'River Heights - Fort Garry': [49.8584, -97.1748],
        'St. Boniface': [49.8864, -97.1080],
        'St. James': [49.8857, -97.2147],
        'St. Norbert - Seine River': [49.7707, -97.1543],
        'St. Vital': [49.8344, -97.1118],
        'Transcona': [49.8966, -97.0010],
        'Waverley West': [49.7927, -97.1854]
    };

    // --- Neighbourhood boundaries ---
    // Loaded up front (rather than lazily for the outline overlay) because marker
    // placement depends on them: see jitterCoordinates.
    //
    // Served from our own origin rather than fetched from data.winnipeg.ca directly. The page
    // reloads every minute, so hitting the City for this on every load meant one request per
    // tab per minute for a file that changes about as often as ward boundaries do. The server
    // holds one copy for everyone and sends cache headers, so the browser rarely re-asks.
    const neighbourhoodFeaturesByName = new Map();
    const neighbourhoodInteriorPoints = new Map();
    const neighbourhoodGeoJsonReady = fetch('/data/neighbourhoods.geojson')
        .then(r => r.json())
        .then(data => {
            for (const feature of data.features || []) {
                const name = feature.properties && feature.properties.name;
                if (name) {
                    neighbourhoodFeaturesByName.set(name.toLowerCase(), feature);
                }
            }
        })
        .catch(() => {});

    function findNeighbourhoodFeature(neighbourhoodName) {
        if (!neighbourhoodName) {
            return null;
        }
        return neighbourhoodFeaturesByName.get(String(neighbourhoodName).toLowerCase()) || null;
    }

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>'"]/g, (char) => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[char]));
    }

    function getIncidentCategory(incidentType) {
        const normalizedType = String(incidentType ?? '').toLowerCase();
        if (normalizedType.startsWith('fire rescue')) {
            return 'fire';
        }
        if (normalizedType.includes('medical response')) {
            return 'medical';
        }
        return 'other';
    }

    function getCategoryColor(incidentType) {
        switch (getIncidentCategory(incidentType)) {
            case 'fire':
                return '#dc3545';
            case 'medical':
                return '#0d6efd';
            default:
                return '#6c757d';
        }
    }

    function isClosed(incident) {
        return incident.CLOSED === true || incident.CLOSED === 'true';
    }

    // GeoJSON rings are [lng, lat]; Leaflet coordinates are [lat, lng].
    function pointInRing(lat, lng, ring) {
        let inside = false;
        for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            const [lngI, latI] = ring[i];
            const [lngJ, latJ] = ring[j];
            const crosses = (latI > lat) !== (latJ > lat)
                && lng < ((lngJ - lngI) * (lat - latI)) / (latJ - latI) + lngI;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    function pointInPolygon(lat, lng, rings) {
        if (!rings.length || !pointInRing(lat, lng, rings[0])) {
            return false;
        }
        // Remaining rings are holes.
        for (let i = 1; i < rings.length; i++) {
            if (pointInRing(lat, lng, rings[i])) {
                return false;
            }
        }
        return true;
    }

    function pointInFeature(coordinates, feature) {
        const geometry = feature && feature.geometry;
        if (!coordinates || !geometry) {
            return false;
        }

        const [lat, lng] = coordinates;
        if (geometry.type === 'Polygon') {
            return pointInPolygon(lat, lng, geometry.coordinates);
        }
        if (geometry.type === 'MultiPolygon') {
            return geometry.coordinates.some(rings => pointInPolygon(lat, lng, rings));
        }
        return false;
    }

    function forEachOuterRing(feature, callback) {
        const geometry = feature.geometry;
        if (geometry.type === 'Polygon') {
            callback(geometry.coordinates[0]);
        } else if (geometry.type === 'MultiPolygon') {
            geometry.coordinates.forEach(rings => callback(rings[0]));
        }
    }

    // A point guaranteed to sit inside the boundary, used when the configured
    // centre does not (the lookup table and Nominatim are both approximate).
    function getInteriorPoint(neighbourhoodName, feature) {
        if (neighbourhoodInteriorPoints.has(neighbourhoodName)) {
            return neighbourhoodInteriorPoints.get(neighbourhoodName);
        }

        let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
        let sumLat = 0, sumLng = 0, vertexCount = 0;
        forEachOuterRing(feature, ring => {
            for (const [lng, lat] of ring) {
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
                minLng = Math.min(minLng, lng);
                maxLng = Math.max(maxLng, lng);
                sumLat += lat;
                sumLng += lng;
                vertexCount++;
            }
        });

        let interiorPoint = null;
        if (vertexCount > 0) {
            const centroid = [sumLat / vertexCount, sumLng / vertexCount];
            if (pointInFeature(centroid, feature)) {
                interiorPoint = centroid;
            } else {
                // Concave or multi-part shape: the centroid can fall outside, so take
                // the grid point inside the boundary that sits closest to it.
                const STEPS = 16;
                let bestDistance = Infinity;
                for (let i = 1; i < STEPS; i++) {
                    for (let j = 1; j < STEPS; j++) {
                        const candidate = [
                            minLat + (maxLat - minLat) * (i / STEPS),
                            minLng + (maxLng - minLng) * (j / STEPS)
                        ];
                        if (!pointInFeature(candidate, feature)) {
                            continue;
                        }
                        const distance = Math.hypot(candidate[0] - centroid[0], candidate[1] - centroid[1]);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            interiorPoint = candidate;
                        }
                    }
                }
            }
        }

        neighbourhoodInteriorPoints.set(neighbourhoodName, interiorPoint);
        return interiorPoint;
    }

    // Incidents are only located to a neighbourhood, so markers are spread around its
    // centre to stop them stacking. The offset is shrunk until the marker lands inside
    // the boundary — a full-size offset overshoots small neighbourhoods.
    const JITTER_STEP = 0.0007;
    const JITTER_SCALES = [1, 0.6, 0.35, 0.2, 0.1];

    function jitterCoordinates(baseCoordinates, seedText, feature) {
        if (!baseCoordinates) {
            return null;
        }

        let hash = 0;
        for (const character of String(seedText ?? '')) {
            hash = ((hash << 5) - hash) + character.charCodeAt(0);
            hash |= 0;
        }

        const latSteps = (hash & 15) - 8;
        const lngSteps = ((hash >> 4) & 15) - 8;

        // No boundary to test against (unknown neighbourhood, ward fallback, or the
        // GeoJSON fetch failed): keep the unconstrained offset.
        if (!feature) {
            return [
                baseCoordinates[0] + latSteps * JITTER_STEP,
                baseCoordinates[1] + lngSteps * JITTER_STEP
            ];
        }

        for (const scale of JITTER_SCALES) {
            const candidate = [
                baseCoordinates[0] + latSteps * JITTER_STEP * scale,
                baseCoordinates[1] + lngSteps * JITTER_STEP * scale
            ];
            if (pointInFeature(candidate, feature)) {
                return candidate;
            }
        }

        // Every offset overshoots (a very small neighbourhood): sit on the centre.
        return pointInFeature(baseCoordinates, feature) ? baseCoordinates : null;
    }

    async function geocodeNeighbourhood(neighbourhoodName) {
        if (!neighbourhoodName) {
            return null;
        }
        if (neighbourhoodCentres[neighbourhoodName]) {
            return neighbourhoodCentres[neighbourhoodName];
        }
        if (neighbourhoodCoordinatesCache[neighbourhoodName]) {
            return neighbourhoodCoordinatesCache[neighbourhoodName];
        }
        if (geocodeFailures.has(neighbourhoodName)) {
            return null;
        }

        // Fallback to Nominatim for any neighbourhood not in the hardcoded table
        const query = encodeURIComponent(`${neighbourhoodName}, Winnipeg, Manitoba, Canada`);
        try {
            const response = await fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${query}`);
            if (!response.ok) {
                geocodeFailures.add(neighbourhoodName);
                return null;
            }

            const results = await response.json();
            if (!results.length) {
                geocodeFailures.add(neighbourhoodName);
                return null;
            }

            const coordinates = [parseFloat(results[0].lat), parseFloat(results[0].lon)];
            neighbourhoodCoordinatesCache[neighbourhoodName] = coordinates;
            return coordinates;
        } catch (error) {
            geocodeFailures.add(neighbourhoodName);
            return null;
        }
    }

    async function getIncidentCoordinates(incident) {
        const neighbourhood = incident.NEIGHBOURHOOD;
        if (!isLocatable(neighbourhood)) {
            return null;
        }

        const feature = findNeighbourhoodFeature(neighbourhood);
        const geocodedCoordinates = await geocodeNeighbourhood(neighbourhood);

        let centre = geocodedCoordinates;
        if (feature && !pointInFeature(centre, feature)) {
            centre = getInteriorPoint(neighbourhood, feature);
        }

        if (centre) {
            const coordinates = jitterCoordinates(centre, incident.INCIDENT_NUMBER, feature);
            if (coordinates) {
                return coordinates;
            }
        }

        const wardCoordinates = wardCentres[incident.WARD];
        if (wardCoordinates) {
            return jitterCoordinates(wardCoordinates, incident.INCIDENT_NUMBER, null);
        }

        return null;
    }

    // --- Settings refs ---
    const optNeighbourhoodOutline = document.getElementById('opt-neighbourhood-outline');
    const optDarkMode = document.getElementById('opt-dark-mode');

    let activeNeighbourhoodLayer = null;

    // --- State ---
    const markersByIncident = new Map();
    const activeCategories = new Set(['fire', 'medical', 'other']);
    let showClosed = localStorage.getItem('wfps_showClosed') !== '0';
    let selectedIncident = null;

    // --- Filters (apply to both markers and table rows) ---
    function isVisible(category, closed) {
        return activeCategories.has(category) && (!closed || showClosed);
    }

    // Driven off the rows rather than the incident array: rows carry their own category and
    // closed state, so this stays correct after a refresh has rebuilt them, and it does not
    // need to re-derive a category the server already worked out.
    function applyFilters() {
        document.querySelectorAll('tr[data-incident]').forEach(row => {
            const visible = isVisible(row.dataset.category, row.dataset.closed === 'true');
            row.classList.toggle('incident-hidden', !visible);

            const marker = markersByIncident.get(row.dataset.incident);
            if (marker && map) {
                visible ? marker.addTo(map) : marker.remove();
            }
        });
    }

    document.querySelectorAll('.filter-btn[data-category]').forEach(btn => {
        btn.addEventListener('click', () => {
            const cat = btn.dataset.category;
            const enabled = !activeCategories.has(cat);
            if (enabled) {
                activeCategories.add(cat);
            } else {
                activeCategories.delete(cat);
            }
            btn.classList.toggle('filter-btn-active', enabled);
            btn.setAttribute('aria-pressed', String(enabled));
            applyFilters();
            track('filter_toggle', { category: cat, enabled: enabled });
        });
    });

    // --- Closed-incident toggle ---
    const closedToggleBtn = document.getElementById('toggle-closed');
    if (closedToggleBtn) {
        closedToggleBtn.classList.toggle('filter-btn-active', showClosed);
        closedToggleBtn.setAttribute('aria-pressed', String(showClosed));
        closedToggleBtn.addEventListener('click', () => {
            showClosed = !showClosed;
            closedToggleBtn.classList.toggle('filter-btn-active', showClosed);
            closedToggleBtn.setAttribute('aria-pressed', String(showClosed));
            localStorage.setItem('wfps_showClosed', showClosed ? '1' : '0');
            applyFilters();
            track('closed_filter_toggle', { enabled: showClosed });
        });
    }

    // --- Neighbourhood outline ---
    function showNeighbourhoodOutline(neighbourhoodName) {
        clearNeighbourhoodOutline();
        if (!map) return;
        const feature = findNeighbourhoodFeature(neighbourhoodName);
        if (!feature) return;
        activeNeighbourhoodLayer = L.geoJSON(feature, {
            style: {
                color: '#ffc107',
                weight: 3,
                fillColor: '#ffc107',
                fillOpacity: 0.15
            }
        }).addTo(map);
    }

    function clearNeighbourhoodOutline() {
        if (activeNeighbourhoodLayer) {
            activeNeighbourhoodLayer.remove();
            activeNeighbourhoodLayer = null;
        }
    }

    // --- Selection ---
    const MARKER_RADIUS = 7;

    function rowFor(incidentNumber) {
        const key = String(incidentNumber);
        const escaped = (window.CSS && typeof CSS.escape === 'function') ? CSS.escape(key) : key;
        return document.querySelector(`tr[data-incident="${escaped}"]`);
    }

    function incidentByNumber(incidentNumber) {
        const key = String(incidentNumber);
        return incidents.find(i => String(i.INCIDENT_NUMBER) === key) || null;
    }

    function clearHighlightedRow() {
        document.querySelectorAll('tr.incident-row-selected').forEach(r => r.classList.remove('incident-row-selected'));
    }

    // One path for every way an incident can be opened — a row, a marker, or a shared link —
    // so all three leave the page in the same state. Selecting works whether or not a marker
    // exists, keeping the table usable when the map is unavailable or a call could not be
    // located.
    function selectIncident(incidentNumber, options) {
        const opts = options || {};
        const key = String(incidentNumber);

        clearHighlightedRow();
        selectedIncident = key;

        const row = rowFor(key);
        if (row) {
            row.classList.add('incident-row-selected');
            if (opts.revealRow) {
                const panel = document.querySelector('.incident-table-panel');
                if (panel && !panel.open) panel.open = true;
                row.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }

        // Makes the selection shareable. replaceState rather than a hash assignment so
        // clicking through a dozen incidents does not bury the back button.
        if (opts.updateHash !== false) {
            const next = '#incident=' + encodeURIComponent(key);
            if (location.hash !== next) {
                history.replaceState(null, '', next);
            }
        }

        const incident = incidentByNumber(key);
        if (optNeighbourhoodOutline && optNeighbourhoodOutline.checked && incident) {
            showNeighbourhoodOutline(incident.NEIGHBOURHOOD);
        } else {
            clearNeighbourhoodOutline();
        }

        const marker = markersByIncident.get(key);
        if (!marker || !map) {
            return;
        }

        if (opts.pan !== false) {
            map.setView(marker.getLatLng(), Math.max(map.getZoom(), 14));
        }
        marker.openPopup();
        marker.setStyle({ radius: 14, weight: 3 });
        setTimeout(() => marker.setStyle({ radius: MARKER_RADIUS, weight: 1 }), 1500);
    }

    function wireRow(row) {
        row.addEventListener('click', () => {
            selectIncident(row.dataset.incident);
            track('incident_open', { source: 'table_row', category: row.dataset.category });
        });
        // The rows carry a click handler and so must be reachable without a mouse; they are
        // given tabindex in the template, which is only half of it without this.
        row.addEventListener('keydown', event => {
            if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'Spacebar') {
                return;
            }
            event.preventDefault();
            selectIncident(row.dataset.incident);
            track('incident_open', { source: 'table_row_keyboard', category: row.dataset.category });
        });
    }

    // --- Table rendering ---
    // These must produce the same markup as the th:each block in index.html: that render is
    // what a crawler and a reader without JavaScript get, this one is what everyone sees
    // from the first refresh onward. Changing one without the other makes the table change
    // shape a minute after it loads.
    const CATEGORY_LABELS = { fire: 'Fire Rescue', medical: 'Medical Response', other: 'Other' };

    // Serialised rather than joined on a delimiter: no separator can collide with a
    // field value, and nothing exotic ends up in the source. This previously joined on
    // a literal NUL, which made git treat the whole file as binary and undiffable.
    function rowSignature(incident) {
        return JSON.stringify([
            incident.INCIDENT_TYPE, incident.IS_MOTOR, incident.NEIGHBOURHOOD,
            incident.CALL_TIME, incident.UNITS, incident.WARD,
            isClosed(incident), incident.DURATION, incident.CLOSED_TIME
        ]);
    }

    function cellsHtml(incident) {
        const category = getIncidentCategory(incident.INCIDENT_TYPE);
        const closed = isClosed(incident);
        const closedLabel = incident.DURATION ? 'Closed · ' + escapeHtml(incident.DURATION) : 'Closed';
        const status = closed
            ? `<span class="badge incident-badge-closed">${closedLabel}</span>`
            : '<span class="incident-status-active">Active</span>';

        return `<td data-label="Incident Type"><span>${escapeHtml(incident.INCIDENT_TYPE)}</span></td>`
            + `<td data-label="Category"><span class="badge incident-badge incident-badge-${category}">`
            + `${CATEGORY_LABELS[category]}</span></td>`
            + `<td data-label="Status">${status}</td>`
            + `<td data-label="Motor Vehicle Incident?"><span>${escapeHtml(incident.IS_MOTOR)}</span></td>`
            + `<td data-label="Neighbourhood"><span>${escapeHtml(incident.NEIGHBOURHOOD)}</span></td>`
            + `<td data-label="Call Time"><span>${escapeHtml(incident.CALL_TIME)}</span></td>`
            + `<td data-label="Units Dispatched"><span>${escapeHtml(incident.UNITS)}</span></td>`
            + `<td data-label="Ward"><span>${escapeHtml(incident.WARD)}</span></td>`
            + `<td data-label="Incident Number"><span>${escapeHtml(incident.INCIDENT_NUMBER)}</span></td>`;
    }

    function applyRowState(row, incident) {
        const category = getIncidentCategory(incident.INCIDENT_TYPE);
        const closed = isClosed(incident);

        row.className = `incident-row incident-row-${category}${closed ? ' incident-row-closed' : ''}`;
        row.dataset.incident = String(incident.INCIDENT_NUMBER);
        row.dataset.closed = String(closed);
        row.dataset.category = category;

        const signature = rowSignature(incident);
        if (row.dataset.sig !== signature) {
            row.innerHTML = cellsHtml(incident);
            row.dataset.sig = signature;
        }
    }

    function buildRow(incident) {
        const row = document.createElement('tr');
        row.tabIndex = 0;
        row.style.cursor = 'pointer';
        applyRowState(row, incident);
        wireRow(row);
        return row;
    }

    // Existing rows are moved rather than recreated, so their listeners, selection and the
    // reader's focus survive a refresh. Only genuinely new calls get new elements.
    function syncTable(list) {
        const tbody = document.getElementById('incident-rows');
        if (!tbody) {
            return;
        }

        const focusedIncident = document.activeElement
            && document.activeElement.matches
            && document.activeElement.matches('tr[data-incident]')
            ? document.activeElement.dataset.incident
            : null;

        const existing = new Map();
        tbody.querySelectorAll('tr[data-incident]').forEach(r => existing.set(r.dataset.incident, r));

        let cursor = tbody.firstElementChild;
        for (const incident of list) {
            const key = String(incident.INCIDENT_NUMBER);
            let row = existing.get(key);
            if (row) {
                existing.delete(key);
                applyRowState(row, incident);
            } else {
                row = buildRow(incident);
            }

            if (cursor === row) {
                cursor = cursor.nextElementSibling;
            } else {
                tbody.insertBefore(row, cursor);
            }
        }

        // Whatever is left was in the feed before and is not now.
        existing.forEach(row => row.remove());

        if (selectedIncident) {
            const row = rowFor(selectedIncident);
            if (row) {
                row.classList.add('incident-row-selected');
            }
        }
        if (focusedIncident) {
            const row = rowFor(focusedIncident);
            if (row) {
                row.focus({ preventScroll: true });
            }
        }
    }

    // --- Markers ---
    function buildPopup(incident) {
        const closed = isClosed(incident);
        const statusLine = closed
            ? `Status: Closed${incident.DURATION ? ' (on scene ' + escapeHtml(incident.DURATION) + ')' : ''}<br/>`
            : 'Status: Active<br/>';
        return `
                <strong>${escapeHtml(incident.INCIDENT_TYPE)}</strong><br/>
                ${statusLine}
                Neighbourhood: ${escapeHtml(incident.NEIGHBOURHOOD)}<br/>
                Units: ${escapeHtml(incident.UNITS ?? 'No Response')}<br/>
                Call Time: ${escapeHtml(incident.CALL_TIME)}
                ${closed && incident.CLOSED_TIME ? '<br/>Closed: ' + escapeHtml(incident.CLOSED_TIME) : ''}
            `;
    }

    async function syncMarkers(list) {
        if (!map) {
            return [];
        }

        // Boundaries must be indexed before placing markers so each one can be constrained
        // to its neighbourhood. Resolves either way — if the fetch failed, markers fall back
        // to unconstrained offsets.
        await neighbourhoodGeoJsonReady;

        const seen = new Set();
        const bounds = [];

        for (const incident of list) {
            const key = String(incident.INCIDENT_NUMBER);
            const closed = isClosed(incident);
            seen.add(key);

            const existingMarker = markersByIncident.get(key);
            if (existingMarker) {
                // Position is a pure function of the incident number, so it never moves.
                // Only the open/closed styling and the popup can change under us.
                if (existingMarker.wfpsClosed !== closed) {
                    existingMarker.setStyle({
                        fillOpacity: closed ? 0.12 : 0.6,
                        dashArray: closed ? '3 3' : null
                    });
                    existingMarker.setPopupContent(buildPopup(incident));
                    existingMarker.wfpsClosed = closed;
                }
                bounds.push(existingMarker.getLatLng());
                continue;
            }

            const coordinates = await getIncidentCoordinates(incident);
            if (!coordinates) {
                continue;
            }

            const markerColor = getCategoryColor(incident.INCIDENT_TYPE);
            const marker = L.circleMarker(coordinates, {
                radius: MARKER_RADIUS,
                color: markerColor,
                fillColor: markerColor,
                fillOpacity: closed ? 0.12 : 0.6,
                weight: 1,
                dashArray: closed ? '3 3' : null
            }).bindPopup(buildPopup(incident));

            marker.wfpsClosed = closed;
            marker.on('click', () => {
                selectIncident(key, { pan: false, revealRow: true });
                track('incident_open', {
                    source: 'map_marker',
                    category: getIncidentCategory(incident.INCIDENT_TYPE)
                });
            });

            markersByIncident.set(key, marker);
            bounds.push(coordinates);
        }

        for (const [key, marker] of markersByIncident) {
            if (!seen.has(key)) {
                marker.remove();
                markersByIncident.delete(key);
            }
        }

        return bounds;
    }

    // --- Map view persistence ---
    // Where the reader last left the map, so a hard reload, a shared link opened later, or
    // the app being reopened from the home screen all come back to their part of the city
    // instead of the whole of Winnipeg. Expires, because a fortnight-old viewport is not
    // where anyone wants to start.
    const VIEW_KEY = 'wfps_mapView';
    const VIEW_MAX_AGE_MS = 12 * 60 * 60 * 1000;

    function saveMapView() {
        if (!map) {
            return;
        }
        try {
            const centre = map.getCenter();
            localStorage.setItem(VIEW_KEY, JSON.stringify({
                lat: centre.lat,
                lng: centre.lng,
                zoom: map.getZoom(),
                at: Date.now()
            }));
        } catch (error) {
            /* Private mode, or storage full. Not worth breaking the map over. */
        }
    }

    function restoreMapView() {
        if (!map) {
            return false;
        }
        try {
            const view = JSON.parse(localStorage.getItem(VIEW_KEY) || 'null');
            if (!view || typeof view.lat !== 'number' || typeof view.lng !== 'number') {
                return false;
            }
            if (!view.at || Date.now() - view.at > VIEW_MAX_AGE_MS) {
                return false;
            }
            map.setView([view.lat, view.lng], view.zoom || 11);
            return true;
        } catch (error) {
            return false;
        }
    }

    // --- Deep links ---
    function incidentFromHash() {
        const match = /^#incident=(.+)$/.exec(location.hash || '');
        if (!match) {
            return null;
        }
        try {
            return decodeURIComponent(match[1]);
        } catch (error) {
            return null;
        }
    }

    // --- Refresh ---
    // Matches the server's polling interval, passed through so the two cannot drift.
    const REFRESH_SECONDS = (window.WFPS_DATA && window.WFPS_DATA.refreshSeconds) || 60;
    const NEW_HIGHLIGHT_MS = 25000;
    const BASE_TITLE = document.title;

    let countdown = REFRESH_SECONDS;
    let refreshing = false;
    let unseenNew = 0;

    const refreshBadge = document.getElementById('refresh-badge');
    const knownIncidents = new Set(incidents.map(i => String(i.INCIDENT_NUMBER)));

    function updateRefreshBadge() {
        if (!refreshBadge) {
            return;
        }
        const m = Math.floor(countdown / 60);
        const s = String(countdown % 60).padStart(2, '0');
        refreshBadge.textContent = `Refreshing in ${m}:${s}`;
    }

    // A backgrounded tab is how a lot of people use this page — it is left open and glanced
    // at. Counting arrivals in the tab title is the only way that glance tells them anything
    // without being brought to the front. Only ever applied while the tab is hidden, so a
    // crawler rendering the page never sees a title that is not the real one.
    function setTitleBadge() {
        document.title = unseenNew > 0 ? `(${unseenNew}) ${BASE_TITLE}` : BASE_TITLE;
    }

    document.addEventListener('visibilitychange', () => {
        if (document.hidden || unseenNew === 0) {
            return;
        }
        track('returned_to_tab', { new_incidents: unseenNew });
        unseenNew = 0;
        setTitleBadge();
    });

    function markArrived(keys) {
        for (const key of keys) {
            const row = rowFor(key);
            if (row) {
                row.classList.add('incident-row-new');
                setTimeout(() => row.classList.remove('incident-row-new'), NEW_HIGHLIGHT_MS);
            }

            const marker = markersByIncident.get(key);
            const element = marker && typeof marker.getElement === 'function' ? marker.getElement() : null;
            if (element) {
                element.classList.add('marker-new');
                setTimeout(() => element.classList.remove('marker-new'), NEW_HIGHLIGHT_MS);
            }
        }
    }

    function applyChrome(data) {
        const answer = document.getElementById('live-summary-answer');
        if (answer && typeof data.summarySentence === 'string') {
            answer.textContent = data.summarySentence;
        }

        const updated = document.getElementById('last-updated');
        if (updated && data.lastUpdatedIso) {
            updated.setAttribute('datetime', data.lastUpdatedIso);
            updated.textContent = data.lastUpdatedDisplay || '';
        }

        const warning = document.getElementById('source-warning');
        if (warning) {
            warning.hidden = data.dataSourceAvailable !== false;
        }
    }

    async function applyUpdate(data) {
        const list = Array.isArray(data.incidents) ? data.incidents : [];
        incidents = list;

        const arrived = list
            .map(incident => String(incident.INCIDENT_NUMBER))
            .filter(key => !knownIncidents.has(key));

        knownIncidents.clear();
        list.forEach(incident => knownIncidents.add(String(incident.INCIDENT_NUMBER)));

        syncTable(list);
        await syncMarkers(list);
        applyFilters();
        applyChrome(data);

        if (arrived.length === 0) {
            return;
        }

        markArrived(arrived);
        track('new_incidents', { count: arrived.length, hidden: document.hidden });
        if (document.hidden) {
            unseenNew += arrived.length;
            setTitleBadge();
        }
    }

    async function refresh() {
        try {
            const response = await fetch('/api/incidents', {
                headers: { 'Accept': 'application/json' },
                cache: 'no-store'
            });
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            await applyUpdate(await response.json());
        } catch (error) {
            // Keep showing the last good data and try again next cycle. The page used to
            // reload on this timer, so a failed fetch was a blank page; now it is a page
            // that has simply not changed.
            track('refresh_failed', {});
        }
    }

    updateRefreshBadge();
    setInterval(async () => {
        if (refreshing) {
            return;
        }
        countdown--;
        updateRefreshBadge();
        if (countdown > 0) {
            return;
        }

        refreshing = true;
        if (refreshBadge) {
            refreshBadge.textContent = 'Refreshing…';
        }
        await refresh();
        countdown = REFRESH_SECONDS;
        refreshing = false;
        updateRefreshBadge();
    }, 1000);

    // --- Map resize ---
    const incidentPanel = document.querySelector('.incident-table-panel');
    if (incidentPanel) {
        incidentPanel.addEventListener('toggle', () => {
            if (map) window.requestAnimationFrame(() => map.invalidateSize());
            track('table_panel_toggle', { open: incidentPanel.open });
        });
    }

    window.addEventListener('resize', () => { if (map) map.invalidateSize(); });

    // --- First render ---
    let interacted = false;
    function noteInteraction(kind) {
        if (interacted) {
            return;
        }
        interacted = true;
        track('map_interaction', { type: kind });
    }

    async function initialise() {
        const loading = document.getElementById('map-loading');
        if (loading) {
            loading.style.display = map ? 'flex' : 'none';
        }

        const bounds = await syncMarkers(incidents);

        // The server rendered these rows; they need their handlers, and a signature so the
        // first refresh can tell an unchanged row from a changed one.
        document.querySelectorAll('tr[data-incident]').forEach(row => {
            const incident = incidentByNumber(row.dataset.incident);
            if (incident) {
                row.dataset.sig = rowSignature(incident);
            }
            wireRow(row);
        });

        applyFilters();

        const deepLinked = incidentFromHash();
        const restored = restoreMapView();
        if (map && !restored && !deepLinked && bounds.length > 0) {
            // Measure the container first: framing against a panel that has not been laid
            // out yet produces a degenerate fit, which on a first load meant opening at
            // maximum zoom on one arbitrary street.
            map.invalidateSize();
            // Unanimated so moveend fires inside this call, before the handlers below are
            // attached. Automatic framing must not be recorded as the reader's own view.
            map.fitBounds(bounds, { padding: [30, 30], animate: false });
        }
        if (deepLinked) {
            selectIncident(deepLinked, { revealRow: true, updateHash: false });
            track('incident_open', { source: 'deep_link' });
        }

        // Attached after the opening view is settled, so automatic framing is never
        // mistaken for the reader choosing where to look.
        if (map) {
            map.on('moveend', saveMapView);
            map.on('zoomend', saveMapView);
            map.on('dragend', () => noteInteraction('pan'));
            map.on('zoomend', () => noteInteraction('zoom'));
        }

        if (loading) {
            loading.style.display = 'none';
        }
    }

    initialise();

    // --- Map click: clear neighbourhood outline ---
    if (map) map.on('click', clearNeighbourhoodOutline);

    // --- Dark mode ---
    function setDarkMode(enabled) {
        document.documentElement.classList.toggle('dark-mode', enabled);
        if (map) {
            if (enabled) {
                if (map.hasLayer(lightTile)) lightTile.remove();
                if (!map.hasLayer(darkTile)) darkTile.addTo(map);
            } else {
                if (map.hasLayer(darkTile)) darkTile.remove();
                if (!map.hasLayer(lightTile)) lightTile.addTo(map);
            }
        }
        localStorage.setItem('wfps_darkMode', enabled ? '1' : '0');
    }

    // Tracked from the change handlers rather than inside setDarkMode, so restoring a saved
    // preference on load is not counted as someone choosing it again.
    optDarkMode.addEventListener('change', () => {
        setDarkMode(optDarkMode.checked);
        track('dark_mode_toggle', { enabled: optDarkMode.checked });
    });
    optNeighbourhoodOutline.addEventListener('change', () => {
        localStorage.setItem('wfps_neighbourhoodOutline', optNeighbourhoodOutline.checked ? '1' : '0');
        if (!optNeighbourhoodOutline.checked) clearNeighbourhoodOutline();
        track('outline_toggle', { enabled: optNeighbourhoodOutline.checked });
    });

    // Restore settings from localStorage
    if (localStorage.getItem('wfps_darkMode') === '1') {
        optDarkMode.checked = true;
        setDarkMode(true);
    }
    if (localStorage.getItem('wfps_neighbourhoodOutline') === '1') {
        optNeighbourhoodOutline.checked = true;
    }

    // --- Settings panel toggle ---
    const settingsBtn = document.getElementById('settings-btn');
    const settingsPanel = document.getElementById('settings-panel');
    settingsBtn.addEventListener('click', e => {
        e.stopPropagation();
        settingsPanel.hidden = !settingsPanel.hidden;
        if (!settingsPanel.hidden) {
            track('settings_open', {});
        }
    });
    document.addEventListener('click', e => {
        if (!settingsPanel.hidden && !settingsPanel.contains(e.target) && e.target !== settingsBtn) {
            settingsPanel.hidden = true;
        }
    });

    // --- Install prompt ---
    // The weakest number this site has is people coming back: almost everyone arrives once
    // and never returns. For something whose whole use is checking on it, a home screen
    // icon is the difference between remembering it exists and not.
    const INSTALL_DISMISSED_KEY = 'wfps_installDismissed';
    const INSTALL_SNOOZE_MS = 30 * 24 * 60 * 60 * 1000;

    const installBar = document.getElementById('install-bar');
    const installText = document.getElementById('install-bar-text');
    const installAccept = document.getElementById('install-accept');
    const installDismiss = document.getElementById('install-dismiss');

    let deferredInstallPrompt = null;

    function standalone() {
        return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches)
            || window.navigator.standalone === true;
    }

    function installRecentlyDismissed() {
        try {
            const at = Number(localStorage.getItem(INSTALL_DISMISSED_KEY) || 0);
            return at > 0 && Date.now() - at < INSTALL_SNOOZE_MS;
        } catch (error) {
            return false;
        }
    }

    function dismissInstallBar(reason) {
        if (installBar) {
            installBar.hidden = true;
        }
        try {
            localStorage.setItem(INSTALL_DISMISSED_KEY, String(Date.now()));
        } catch (error) {
            /* Nothing to do; it will simply be offered again. */
        }
        track('install_dismissed', { reason: reason });
    }

    function showInstallBar(mode) {
        if (!installBar || standalone() || installRecentlyDismissed()) {
            return;
        }
        // iOS has no install prompt to defer, so the only thing on offer there is the
        // instruction. Hiding the button avoids a control that could not do anything.
        if (mode === 'ios') {
            if (installText) {
                installText.textContent = 'Add this map to your home screen: tap Share, then Add to Home Screen.';
            }
            if (installAccept) {
                installAccept.hidden = true;
            }
        }
        installBar.hidden = false;
        track('install_prompt_shown', { mode: mode });
    }

    window.addEventListener('beforeinstallprompt', event => {
        event.preventDefault();
        deferredInstallPrompt = event;
        showInstallBar('browser');
    });

    if (installAccept) {
        installAccept.addEventListener('click', async () => {
            if (!deferredInstallPrompt) {
                return;
            }
            track('install_accepted', {});
            deferredInstallPrompt.prompt();
            const choice = await deferredInstallPrompt.userChoice.catch(() => null);
            deferredInstallPrompt = null;
            installBar.hidden = true;
            track('install_choice', { outcome: choice ? choice.outcome : 'unknown' });
        });
    }

    if (installDismiss) {
        installDismiss.addEventListener('click', () => dismissInstallBar('user'));
    }

    window.addEventListener('appinstalled', () => {
        if (installBar) {
            installBar.hidden = true;
        }
        track('app_installed', {});
    });

    // iOS never fires beforeinstallprompt, so the offer has to be made on inspection.
    const iosSafari = /iphone|ipad|ipod/i.test(window.navigator.userAgent)
        && !/crios|fxios|edgios/i.test(window.navigator.userAgent);
    if (iosSafari && !standalone()) {
        showInstallBar('ios');
    }

    // Distinguishes a launch from the home screen from a visit in a browser tab, which is
    // the only way to tell whether any of the above is worth keeping.
    if (standalone()) {
        track('app_launch_standalone', {});
    }

    // --- Service worker ---
    // Registered for installability and an honest offline page, not for caching; see sw.js.
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/sw.js').catch(() => {
                /* No service worker means no install prompt, and nothing else changes. */
            });
        });
    }

})();
