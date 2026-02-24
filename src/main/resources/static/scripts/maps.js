/**
 * @license
 * Copyright 2019 Google LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
let map;
let infowindow;


async function initMap() {
  // Request needed libraries.
  const { Map } = await google.maps.importLibrary("maps");
  const { AdvancedMarkerElement } = await google.maps.importLibrary("marker");
  const {PlacesService} = await google.maps.importLibrary("places")
  const center = { lat: 49.8536638, lng: -97.3176417 };
  const map = new Map(document.getElementById("map"), {
    zoom: 11,
    center,
    mapId: "4504f8b37365c3d0",
  });

  for (const incident of incidents) {
    const AdvancedMarkerElement = new google.maps.marker.AdvancedMarkerElement({
      map,
      content: buildContent(incident),
      position: incident.position,
      title: incident.description,
    });

    AdvancedMarkerElement.addListener("click", () => {
      toggleHighlight(AdvancedMarkerElement, incident);
    });
  }

}




function findLocationFromPlaces() {
  let placesService = new google.maps.places.PlacesService(document.getElementById("map")); // i.e. <div id="map"></div>

  const request = {
      query: 'San Francisco',
      fields: ['photos', 'formatted_address', 'name', 'rating', 'opening_hours', 'geometry']
  };

  placesService.findPlaceFromQuery(request, function(results, status) {
      if (status === google.maps.places.PlacesServiceStatus.OK) {
          console.log(results[0].formatted_address) // San Francisco, CA, USA
      }
      else {
          console.log("doesn't work");
      }
  });
}


function toggleHighlight(markerView, incident) {
  if (markerView.content.classList.contains("highlight")) {
    markerView.content.classList.remove("highlight");
    markerView.zIndex = null;
  } else {
    markerView.content.classList.add("highlight");
    markerView.zIndex = 1;
  }
}

function buildContent(incident) {
  const content = document.createElement("div");

  content.classList.add("incident");
  content.innerHTML = `
    <div class="icon">
        <i aria-hidden="true" class="fa fa-icon fa-${incident.type}" title="${incident.description}"></i>
        <span class="fa-sr-only">${incident.type}</span>
    </div>
    <div class="details">
        <div class="type">${incident.description}</div>
        <div class="address">${incident.address}</div>
        <div class="features">
        <div>
            <i aria-hidden="true" class="fa fa-truck fa-lg truck" title="Units"></i>
            <span class="fa-sr-only">Units</span>
            <span>${incident.units}</span>
        </div>
        </div>
    </div>
    `;
  return content;
}

const incidents = [
  {
    address: "Shaughnessy Park",
    description: "House Fire",
    type: "house-fire",
    position: {lat: 49.9286529, lng: -97.1806239},
    units: "R5, R6, E6, E17",
  }
]

initMap();
