package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(FeedController.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test",
        "app.authorName=Test Author"
})
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Database database;

    private static Map<String, Object> incident(String number, String type, String neighbourhood,
                                                boolean closed, String callIso, String closedIso) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("INCIDENT_NUMBER", number);
        row.put("INCIDENT_TYPE", type);
        row.put("NEIGHBOURHOOD", neighbourhood);
        row.put("UNITS", "E1, E2");
        row.put("WARD", "Point Douglas");
        row.put("CALL_TIME", "August 25, 2026 at 09:30");
        row.put("CALL_TIME_ISO", callIso);
        row.put("CLOSED", closed);
        row.put("CLOSED_TIME_ISO", closedIso);
        row.put("DURATION", closed ? "42m" : "");
        return row;
    }

    private String fetchFeed() throws Exception {
        return mockMvc.perform(get("/feed.xml")).andReturn().getResponse().getContentAsString();
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Test
    void feedIsWellFormedAndCarriesAnEntryPerIncident() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("111", "Fire Rescue - Alarm", "Wolseley", false, "2026-08-25T09:30:00-05:00", ""),
                incident("222", "Medical Response", "St. Vital", true, "2026-08-25T08:00:00-05:00", "2026-08-25T08:42:00-05:00")));

        Document feed = parse(fetchFeed());

        assertThat(feed.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry").getLength()).isEqualTo(2);
    }

    /**
     * Incident types and neighbourhoods come from an external feed. One unescaped ampersand
     * makes the whole document unparseable, taking every entry down with it.
     */
    @Test
    void hostileFieldValuesCannotBreakTheDocument() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("333", "Rescue & Extrication <urgent>", "O'Connor \"Park\" & Sons",
                        false, "2026-08-25T09:30:00-05:00", "")));

        Document feed = parse(fetchFeed());

        assertThat(feed.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry").getLength()).isEqualTo(1);
        assertThat(feed.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "title").item(1).getTextContent())
                .contains("Rescue & Extrication <urgent>");
    }

    /** A closed call has changed since it was published; Atom's updated is what says so. */
    @Test
    void closedIncidentReportsTheCloseTimeAsUpdated() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("222", "Medical Response", "St. Vital", true,
                        "2026-08-25T08:00:00-05:00", "2026-08-25T08:42:00-05:00")));

        String xml = fetchFeed();

        assertThat(xml).contains("<published>2026-08-25T08:00:00-05:00</published>");
        assertThat(xml).contains("<updated>2026-08-25T08:42:00-05:00</updated>");
    }

    /** An entry with no parseable time cannot be dated, and an undated Atom entry is invalid. */
    @Test
    void incidentWithoutAParseableCallTimeIsSkipped() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of(
                incident("444", "Fire Rescue - Alarm", "Wolseley", false, "", "")));

        Document feed = parse(fetchFeed());

        assertThat(feed.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry").getLength()).isZero();
    }

    @Test
    void feedIsCappedForReaders() throws Exception {
        List<Map<String, Object>> many = new ArrayList<>();
        IntStream.range(0, 120).forEach(i ->
                many.add(incident("n" + i, "Medical Response", "Wolseley", false, "2026-08-25T09:30:00-05:00", "")));
        when(database.getRecentIncidents()).thenReturn(many);

        Document feed = parse(fetchFeed());

        assertThat(feed.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry").getLength()).isEqualTo(50);
    }

    @Test
    void emptyFeedIsStillValidXml() throws Exception {
        when(database.getRecentIncidents()).thenReturn(List.of());

        Document feed = parse(fetchFeed());

        assertThat(feed.getDocumentElement().getLocalName()).isEqualTo("feed");
    }
}
