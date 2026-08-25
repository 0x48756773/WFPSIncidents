package ca.jdsecurity.incidents.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Publishes the site's public identity to every rendered view so templates never
 * hardcode the domain. Absolute URLs (canonical, Open Graph, JSON-LD) must agree
 * with the origin actually being served: a canonical pointing at a domain that
 * redirects back here is a conflicting signal to crawlers.
 */
@ControllerAdvice
public class SiteIdentityAdvice {

    private final String baseUrl;
    private final String contactEmail;
    private final String authorName;
    private final RefreshCadence refreshCadence;

    public SiteIdentityAdvice(
            @Value("${app.baseUrl}") String baseUrl,
            @Value("${app.contactEmail}") String contactEmail,
            @Value("${app.authorName}") String authorName,
            RefreshCadence refreshCadence) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.contactEmail = contactEmail;
        this.authorName = authorName;
        this.refreshCadence = refreshCadence;
    }

    /** Reads after "every", so the copy states the configured cadence rather than a literal. */
    @ModelAttribute("refreshLabel")
    public String refreshLabel() {
        return refreshCadence.getLabel();
    }

    @ModelAttribute("refreshSeconds")
    public int refreshSeconds() {
        return refreshCadence.getSeconds();
    }

    /** Named author. Attribution is an E-E-A-T signal, so it lives in one place, not three. */
    @ModelAttribute("authorName")
    public String authorName() {
        return authorName;
    }

    /** Canonical origin with no trailing slash, e.g. {@code https://wfps.redspectrum.ca}. */
    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return baseUrl;
    }

    // The footer address is assembled in the browser from these two halves rather than
    // emitted whole, so the complete address never appears as a contiguous string in the
    // HTML source. That anti-harvesting behaviour predates this class; splitting here
    // keeps it while making the address configurable.

    @ModelAttribute("contactUser")
    public String contactUser() {
        int at = contactEmail.indexOf('@');
        return at < 0 ? contactEmail : contactEmail.substring(0, at);
    }

    @ModelAttribute("contactDomain")
    public String contactDomain() {
        int at = contactEmail.indexOf('@');
        return at < 0 ? "" : contactEmail.substring(at + 1);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
