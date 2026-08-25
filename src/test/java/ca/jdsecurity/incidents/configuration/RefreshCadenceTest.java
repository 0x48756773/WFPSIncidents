package ca.jdsecurity.incidents.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshCadenceTest {

    /** The label is dropped in after the word "every", so it has to read naturally at any interval. */
    @ParameterizedTest
    @CsvSource({
            "60,  minute",
            "120, 2 minutes",
            "300, 5 minutes",
            "30,  30 seconds",
            "90,  90 seconds"
    })
    void labelReadsNaturallyAfterEvery(int seconds, String expected) {
        assertThat(new RefreshCadence(seconds).getLabel()).isEqualTo(expected);
    }

    @Test
    void secondsAreExposedForTheBrowserTimer() {
        assertThat(new RefreshCadence(60).getSeconds()).isEqualTo(60);
    }

    /** A zero or negative interval would spin the scheduler; fail at startup rather than at runtime. */
    @Test
    void nonPositiveIntervalIsRejected() {
        assertThatThrownBy(() -> new RefreshCadence(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshCadence(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
