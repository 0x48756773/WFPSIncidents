package ca.jdsecurity.incidents.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code application.properties} is tracked, so anything written into it is published.
 *
 * <p>This is not hypothetical. A CARTO basemap key was pasted straight into the file and
 * committed, and it stayed there through review — which is the same way the City of
 * Winnipeg token had long been assumed to have leaked. Secrets belong in the environment,
 * or in the git-ignored {@code .env} the file imports; what stays here is the name of the
 * variable to read.
 *
 * <p>The file is read as raw text rather than through {@code Environment}, because the
 * point is what the committed file says, not what it resolves to at runtime.
 */
class NoCommittedSecretsTest {

    /** Properties known to carry a credential. Add to this when another one appears. */
    private static final List<String> SECRET_KEYS = List.of(
            "secret.cityOfWinnipeg",
            "app.mapTilesKey");

    /**
     * Catches the key nobody thought to add above: a long opaque run of token-ish
     * characters. Real configuration here does not look like this — URLs carry slashes,
     * addresses an {@code @}, names a space, and the rest are short or numeric.
     */
    private static final Pattern TOKEN_SHAPED = Pattern.compile("^[A-Za-z0-9_\\-]{24,}$");

    private static final Pattern ASSIGNMENT = Pattern.compile("^([^#=\\s]+)\\s*=\\s*(.*)$");

    private static String properties() throws IOException {
        return new String(new ClassPathResource("application.properties").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static String valueOf(String contents, String key) {
        Matcher matcher = Pattern.compile("^" + Pattern.quote(key) + "\\s*=\\s*(.*)$", Pattern.MULTILINE)
                .matcher(contents);
        assertThat(matcher.find()).as("no %s in application.properties", key).isTrue();
        return matcher.group(1).trim();
    }

    @Test
    void everyKnownSecretIsReadFromTheEnvironmentRatherThanWrittenDown() throws IOException {
        String contents = properties();

        for (String key : SECRET_KEYS) {
            assertThat(valueOf(contents, key))
                    .as("%s must read from the environment, e.g. ${SOME_VAR:} -- see .env.example", key)
                    .matches("^\\$\\{[A-Za-z0-9_]+(:.*)?}$");
        }
    }

    /**
     * The import is what lets a developer keep real values in a git-ignored {@code .env}.
     * Without it the placeholders above resolve to empty for everyone who has not set
     * shell variables, and the failure is silent.
     */
    @Test
    void aLocalEnvFileIsImportedIfPresent() throws IOException {
        assertThat(properties())
                .contains("spring.config.import=optional:file:./.env[.properties]");
    }

    /** Optional, so a checkout with no .env still starts rather than failing outright. */
    @Test
    void theEnvImportIsOptional() throws IOException {
        String value = valueOf(properties(), "spring.config.import");

        assertThat(value)
                .as("a required import makes the app refuse to start without a .env")
                .startsWith("optional:");
    }

    @Test
    void noPropertyValueLooksLikeAPastedCredential() throws IOException {
        List<String> suspects = new ArrayList<>();

        for (String line : properties().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            Matcher matcher = ASSIGNMENT.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String value = matcher.group(2).trim();
            if (value.startsWith("${")) {
                continue;
            }
            if (TOKEN_SHAPED.matcher(value).matches()) {
                suspects.add(matcher.group(1));
            }
        }

        assertThat(suspects)
                .as("these look like credentials written into a tracked file; move them to .env "
                        + "and read them with ${VAR:} instead")
                .isEmpty();
    }
}
