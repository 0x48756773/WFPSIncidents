package ca.jdsecurity.incidents.service;

import ca.jdsecurity.incidents.database.Database;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class CityOfWinnipegService {
    ObjectMapper objectMapper = new ObjectMapper();
    String cityOfWinnipegSecret;
    String googleApiKey;
    String cityOfWinnipegHost;
    String cityOfWinipegPath;
    String cityOfWinnipegQuery;

    private static final Logger log = LoggerFactory.getLogger(CityOfWinnipegService.class);

    public CityOfWinnipegService(String cityOfWinnipegSecret, String cityOfWinnipegHost, String cityOfWinipegPath, String cityOfWinnipegQuery) {
        this.cityOfWinnipegSecret = cityOfWinnipegSecret;
        this.cityOfWinnipegHost = cityOfWinnipegHost;
        this.cityOfWinipegPath = cityOfWinipegPath;
        this.cityOfWinnipegQuery = cityOfWinnipegQuery;
    }

    public List<HashMap<String, Object>> getAllIncidents() throws JsonProcessingException, UnirestException {
        log.info("Retrieving Incidents from City of Winnipeg");
        HttpResponse<JsonNode> jsonResponse
                = Unirest.get(this.cityOfWinnipegHost + this.cityOfWinipegPath)
                .header("$$app_token", this.cityOfWinnipegSecret)
                .queryString("$where", this.cityOfWinnipegQuery).asJson();

        log.info("Status: " + jsonResponse.getStatus());
        log.info("Status Text: " + jsonResponse.getStatusText());

        JSONArray incidentArray = jsonResponse.getBody().getArray();

        List<HashMap<String, Object>> incidentList = new ArrayList<>();
        List<String> neighbourhoodList = new ArrayList<>();
        for (int i = 0; i < incidentArray.length(); i++) {
            HashMap<String,Object> result = objectMapper.readValue(incidentArray.getJSONObject(i).toString(), HashMap.class);
            incidentList.add(result);
            neighbourhoodList.add((String) result.get("neighbourhood"));
        }

        return incidentList;
    }
}
