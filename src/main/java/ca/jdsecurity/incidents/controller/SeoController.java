package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.database.Database;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serves robots.txt and sitemap.xml. These were static files under /static, which meant
 * they carried a hardcoded domain and a sitemap that could never report a real lastmod.
 * Rendering them keeps both in step with {@code app.baseUrl}: a sitemap listing URLs on a
 * different host than the one serving it is treated as cross-domain and ignored.
 */
@Controller
public class SeoController {

    private final Database database;
    private final String baseUrl;

    public SeoController(Database database, @Value("${app.baseUrl}") String baseUrl) {
        this.database = database;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robots() {
        return """
                User-agent: *
                Allow: /

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        // changefreq and priority are deliberately absent: Google confirmed it ignores both.
        // lastmod is the hint it does use, so it is omitted rather than guessed when no sync
        // has succeeded — a fabricated timestamp is worse than none.
        ZonedDateTime lastSync = database.getLastSuccessfulSync();
        String lastmod = lastSync == null
                ? ""
                : "        <lastmod>%s</lastmod>\n".formatted(lastSync.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        // /about carries no lastmod: it changes only on deploy, and the sync time that
        // dates the incident list says nothing about when this copy was last edited.
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                    <url>
                        <loc>%s/</loc>
                %s    </url>
                    <url>
                        <loc>%s/about</loc>
                    </url>
                </urlset>
                """.formatted(baseUrl, lastmod, baseUrl);
    }
}
