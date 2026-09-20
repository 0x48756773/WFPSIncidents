package ca.jdsecurity.incidents.incident;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentCategoryTest {

    /**
     * The grouping matched only types beginning "Fire Rescue", so "Fire Response" — which the
     * feed publishes constantly — rendered grey, as an Other call.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Fire Response",
            "Fire Rescue - Alarm",
            "Vehicle Fire",
            "Outside Fire",
            "fire response"
    })
    void typesNamingAFireAreGroupedWithFire(String incidentType) {
        assertThat(IncidentCategory.of(incidentType)).isEqualTo(IncidentCategory.FIRE);
    }

    @Test
    void medicalCallsAreUnaffected() {
        assertThat(IncidentCategory.of("Medical Response")).isEqualTo(IncidentCategory.MEDICAL);
        assertThat(IncidentCategory.of("MEDICAL RESPONSE - TRANSFER")).isEqualTo(IncidentCategory.MEDICAL);
    }

    /** "Fire" has to be its own word: a firearm call is not a fire. */
    @Test
    void fireIsMatchedAsAWholeWord() {
        assertThat(IncidentCategory.of("Medical Response - Firearm")).isEqualTo(IncidentCategory.MEDICAL);
        assertThat(IncidentCategory.of("Firearm Injury")).isEqualTo(IncidentCategory.OTHER);
    }

    /**
     * A type reporting that there was no fire is an alarm call, not a fire — including when it
     * opens with the word, since the outcome is what the type is telling you.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Alarm - No Fire",
            "alarm - no fire",
            "Fire Alarm - No Fire",
            "Non-Fire Rescue"
    })
    void typesReportingNoFireStayInOther(String incidentType) {
        assertThat(IncidentCategory.of(incidentType)).isEqualTo(IncidentCategory.OTHER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Motor Vehicle Incident", "Alarm", "Rescue"})
    void everythingElseIsOther(String incidentType) {
        assertThat(IncidentCategory.of(incidentType)).isEqualTo(IncidentCategory.OTHER);
    }

    /** The type column is free text from upstream and has arrived empty before. */
    @ParameterizedTest
    @NullAndEmptySource
    void missingTypeIsOtherRatherThanAFailure(String incidentType) {
        assertThat(IncidentCategory.of(incidentType)).isEqualTo(IncidentCategory.OTHER);
    }
}
