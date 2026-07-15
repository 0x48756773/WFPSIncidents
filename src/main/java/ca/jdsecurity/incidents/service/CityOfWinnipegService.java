package ca.jdsecurity.incidents.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class CityOfWinnipegService {

    private static final Logger log = LoggerFactory.getLogger(CityOfWinnipegService.class);
    private static final ZoneId WINNIPEG = ZoneId.of("America/Winnipeg");
    // The dataset stores floating (zone-less) local timestamps, e.g. 2026-05-29T09:18:28.000
    private static final DateTimeFormatter SOQL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String secret;
    private final String host;
    private final String path;
    private final int limit;
    private final int closedWindowHours;
    private final int callWindowHours;

    public CityOfWinnipegService(
            @Value("${secret.cityOfWinnipeg}") String secret,
            @Value("${endpoint.cityOfWinnipeg.host}") String host,
            @Value("${endpoint.cityOfWinnipeg.path}") String path,
            @Value("${endpoint.cityOfWinnipeg.limit:200}") int limit,
            @Value("${endpoint.cityOfWinnipeg.closedWindowHours:2}") int closedWindowHours,
            @Value("${endpoint.cityOfWinnipeg.callWindowHours:24}") int callWindowHours) {
        this.secret = secret;
        this.host = host;
        this.path = path;
        this.limit = limit;
        this.closedWindowHours = closedWindowHours;
        this.callWindowHours = callWindowHours;
    }

    /** How far back incidents are kept, by call time. Shared with the retention filter in Database. */
    public int getCallWindowHours() {
        return callWindowHours;
    }

    public List<HashMap<String, Object>> getAllIncidents() throws JsonProcessingException, UnirestException {
        log.info("Retrieving incidents from City of Winnipeg (called within last {}h; open or closed within last {}h)",
                callWindowHours, closedWindowHours);

        LocalDateTime now = LocalDateTime.now(WINNIPEG);
        String closedThreshold = now.minusHours(closedWindowHours).format(SOQL_TIMESTAMP);
        // Bound by call time as well. Without it the query also matches every incident that
        // was never given a closed_time — hundreds of stale records the retention filter
        // discards anyway, which crowd out displayable incidents under $limit.
        String callThreshold = now.minusHours(callWindowHours).format(SOQL_TIMESTAMP);
        String where = "call_time > '" + callThreshold + "'"
                + " AND (closed_time IS NULL OR closed_time > '" + closedThreshold + "')";

        HttpResponse<JsonNode> jsonResponse = Unirest.get(this.host + this.path)
                .header("$$app_token", this.secret)
                .queryString("$where", where)
                .queryString("$order", "call_time DESC")
                .queryString("$limit", this.limit)
                .asJson();

        log.info("City of Winnipeg API responded {} {}", jsonResponse.getStatus(), jsonResponse.getStatusText());

        if (jsonResponse.getStatus() < 200 || jsonResponse.getStatus() >= 300) {
            throw new RuntimeException("Data source returned HTTP " + jsonResponse.getStatus() + " " + jsonResponse.getStatusText());
        }

        JSONArray incidentArray = jsonResponse.getBody().getArray();
        if (incidentArray.length() == this.limit) {
            // Every record returned is now displayable, so hitting the limit means the map is
            // genuinely missing incidents (the oldest by call time) rather than dropping stale ones.
            log.warn("API returned exactly the {}-record limit; the oldest incidents in the {}h window "
                    + "are being dropped. Raise endpoint.cityOfWinnipeg.limit.", this.limit, callWindowHours);
        }

        List<HashMap<String, Object>> incidentList = new ArrayList<>();
        for (int i = 0; i < incidentArray.length(); i++) {
            HashMap<String, Object> result = objectMapper.readValue(incidentArray.getJSONObject(i).toString(), HashMap.class);
            incidentList.add(result);
        }

        return incidentList;
    }
}
