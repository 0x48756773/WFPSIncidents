package ca.jdsecurity.incidents.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A missing URL must return a real 404. A "soft 404" — an error page served with HTTP 200 —
 * gets the page indexed as though it were real content, which becomes a live risk once
 * generated routes exist and a bad slug can be requested.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /** Requested the way a browser or crawler does — without this header Spring negotiates to JSON. */
    private ResponseEntity<String> getAsBrowser(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void missingPageReturnsRealNotFoundStatus() {
        assertThat(getAsBrowser("/no-such-page").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void errorPageIsMarkedNoindex() {
        assertThat(getAsBrowser("/no-such-page").getBody())
                .contains("<meta name=\"robots\" content=\"noindex\">");
    }
}
