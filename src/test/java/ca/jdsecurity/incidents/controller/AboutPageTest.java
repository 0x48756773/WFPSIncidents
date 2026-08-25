package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ca.jdsecurity.incidents.configuration.RefreshCadence;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AboutController.class)
@Import(RefreshCadence.class)
@TestPropertySource(properties = {
        "app.baseUrl=https://example.test",
        "app.contactEmail=hello@example.test"
})
class AboutPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CityOfWinnipegService cityOfWinnipegService;

    private String render() throws Exception {
        return mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aboutPageIsIndexableAndCanonicalToItself() throws Exception {
        String html = render();

        assertThat(html).contains("<link rel=\"canonical\" href=\"https://example.test/about\">");
        assertThat(html).doesNotContain("noindex");
    }

    /**
     * The retention windows are quoted from configuration, not written into the copy, so the
     * page cannot end up describing behaviour the app no longer has.
     */
    @Test
    void retentionWindowsComeFromConfigurationNotProse() throws Exception {
        when(cityOfWinnipegService.getCallWindowHours()).thenReturn(48);
        when(cityOfWinnipegService.getClosedWindowHours()).thenReturn(6);

        String html = render();

        assertThat(html).contains(">6<").contains(">48<");
        assertThat(html).doesNotContain(">24<");
    }

    /** The claim that this site is not affiliated with WFPS must survive any edit to the copy. */
    @Test
    void aboutPageStatesNonAffiliationAndEmergencyNumber() throws Exception {
        String html = render();

        assertThat(html).contains("not affiliated with WFPS");
        assertThat(html).contains("call 911");
    }
}
