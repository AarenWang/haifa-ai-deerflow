package org.wrj.haifa.ai.utilitymcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException;
import org.wrj.haifa.ai.utilitymcp.provider.JsonPostProvider;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ProviderPayload;

class PublicUtilityServicesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void searchesAndResolvesResearchMetadata() {
        JsonProvider crossref = (path, query) -> path.startsWith("/works/")
                ? payload(path, """
                    {"message":{"DOI":"10.1000/test","title":["Agent Memory"],"type":"journal-article",
                    "author":[{"given":"Ada","family":"Lovelace"}],"published":{"date-parts":[[2026,9,1]]},
                    "is-referenced-by-count":7,"URL":"https://doi.org/10.1000/test"}}
                    """)
                : payload(path, """
                    {"message":{"items":[{"DOI":"10.1000/test","title":["Agent Memory"],"type":"journal-article",
                    "author":[{"given":"Ada","family":"Lovelace"}],"published":{"date-parts":[[2026,9,1]]},
                    "is-referenced-by-count":7,"URL":"https://doi.org/10.1000/test"}]}}
                    """);
        JsonProvider openAlex = provider("""
                {"results":[{"id":"https://openalex.org/A1","display_name":"Ada Lovelace",
                "orcid":"https://orcid.org/0000-0000-0000-0001","works_count":12,"cited_by_count":99,
                "last_known_institutions":[{"display_name":"Analytical Engine Institute"}]}]}
                """);
        ResearchService service = new ResearchService(crossref, openAlex);

        assertThat(service.search(Map.of("query", "agent memory", "fromYear", 2026)).data().get("results").toString())
                .contains("10.1000/test").contains("Ada Lovelace");
        assertThat(service.get(Map.of("doi", "10.1000/test")).data())
                .containsEntry("title", "Agent Memory")
                .containsEntry("published", "2026-09-01");
        assertThat(service.doiLookup(Map.of("title", "Agent Memory", "year", 2026)).data().get("candidates").toString())
                .contains("10.1000/test");
        assertThat(service.authorSearch(Map.of("query", "Ada Lovelace")).data().get("results").toString())
                .contains("A1").contains("Analytical Engine Institute");
        assertThatThrownBy(() -> service.get(Map.of("doi", "not-a-doi")))
                .isInstanceOf(UtilityToolException.class);
    }

    @Test
    void normalizesMavenNpmAndPypiRegistries() {
        JsonProvider maven = (path, query) -> payload(path,
                "gav".equals(query.get("core"))
                        ? "{\"response\":{\"docs\":[{\"v\":\"1.2.0\"},{\"v\":\"1.1.0\"}]}}"
                        : "{\"response\":{\"docs\":[{\"g\":\"org.example\",\"a\":\"demo\",\"latestVersion\":\"1.2.0\",\"versionCount\":2}]}}" );
        JsonProvider npm = (path, query) -> path.startsWith("/-/v1/search")
                ? payload(path, """
                    {"objects":[{"package":{"name":"demo","version":"2.0.0","description":"Demo package"},"searchScore":0.9}]}
                    """)
                : payload(path, """
                    {"name":"demo","description":"Demo package","dist-tags":{"latest":"2.0.0"},
                    "versions":{"1.0.0":{},"2.0.0":{}},"homepage":"https://example.test"}
                    """);
        JsonProvider pypi = provider("""
                {"info":{"name":"demo","version":"3.0.0","summary":"Demo Python package"},
                "releases":{"2.0.0":[],"3.0.0":[]}}
                """);
        PackageRegistryService service = new PackageRegistryService(maven, npm, pypi);

        assertThat(service.search(Map.of("ecosystem", "MAVEN", "query", "demo")).data().get("results").toString())
                .contains("org.example:demo");
        assertThat(service.latest(Map.of("ecosystem", "NPM", "package", "demo")).data())
                .containsEntry("latestVersion", "2.0.0");
        assertThat(service.latest(Map.of("ecosystem", "NPM", "package", "@scope/demo")).data())
                .containsEntry("latestVersion", "2.0.0");
        assertThat(service.versions(Map.of("ecosystem", "PYPI", "package", "demo")).data().get("versions").toString())
                .contains("3.0.0");
    }

    @Test
    void mapsOsvVulnerabilities() {
        JsonPostProvider osv = (path, body) -> payload(path, """
                {"vulns":[{"id":"OSV-2026-1","summary":"Example issue","aliases":["CVE-2026-0001"],
                "published":"2026-01-01T00:00:00Z","modified":"2026-02-01T00:00:00Z"}]}
                """);
        JsonProvider osvGet = provider("""
                {"id":"OSV-2026-1","summary":"Example issue","details":"Detailed advisory",
                "aliases":["CVE-2026-0001"],"references":[{"type":"ADVISORY","url":"https://example.test/advisory"}]}
                """);
        OsvService service = new OsvService(osv, osvGet);

        assertThat(service.query(Map.of("ecosystem", "MAVEN", "package", "org.example:demo", "version", "1.0.0")).data())
                .containsEntry("count", 1);
        assertThat(service.query(Map.of("ecosystem", "NPM", "package", "demo", "version", "1.0.0"))
                .data().get("vulnerabilities").toString()).contains("CVE-2026-0001");
        var res = service.get(Map.of("id", "OSV-2026-1"));
        assertThat(res.data())
                .containsEntry("summary", "Example issue")
                .containsEntry("details", "Detailed advisory");
        assertThat(res.meta().get("partial")).isEqualTo(false);

        JsonProvider osvGetLongSummary = provider("""
                {"id":"OSV-2026-2","summary":"%s","details":"advisory"}
                """.formatted("s".repeat(2_001)));
        OsvService serviceLongSummary = new OsvService(osv, osvGetLongSummary);
        assertThat(serviceLongSummary.get(Map.of("id", "OSV-2026-2")).meta().get("partial")).isEqualTo(true);

        JsonProvider osvGetManyAliases = provider("""
                {"id":"OSV-2026-3","aliases":%s}
                """.formatted("[" + String.join(",", java.util.Collections.nCopies(35, "\"CVE-TEST\"")) + "]"));
        OsvService serviceManyAliases = new OsvService(osv, osvGetManyAliases);
        assertThat(serviceManyAliases.get(Map.of("id", "OSV-2026-3")).meta().get("partial")).isEqualTo(true);
    }

    @Test
    void resolvesDnsIncludingSrvUnderscoreLabels() {
        JsonProvider dns = provider("""
                {"Status":0,"AD":true,"Answer":[{"name":"_sip._tcp.example.com.","type":33,"TTL":60,
                "data":"10 5 5060 sip.example.com."}]}
                """);
        InternetService service = new InternetService(dns);

        assertThat(service.dnsQuery(Map.of("name", "_sip._tcp.example.com", "type", "SRV")).data())
                .containsEntry("status", 0)
                .containsEntry("authenticatedData", true);
        assertThat(service.dnsQuery(Map.of("name", "例子.测试", "type", "A")).data().get("name").toString())
                .startsWith("xn--");
    }

    @Test
    void searchesAndReadsWorldBankIndicators() {
        JsonProvider worldBank = (path, query) -> path.contains("/search/")
                ? payload(path, """
                    {"source":[{"concept":[{"id":"Series","variable":[{"id":"NY.GDP.PCAP.CD",
                    "name":"GDP per capita (current US$)","metatype":[{"value":"GDP per capita"}]}]}]}]}
                    """)
                : payload(path, """
                    [{"page":1,"pages":1,"per_page":100,"total":2},
                    [{"indicator":{"id":"NY.GDP.PCAP.CD","value":"GDP per capita (current US$)"},
                    "country":{"id":"SG","value":"Singapore"},"countryiso3code":"SGP","date":"2025","value":90000.0,"obs_status":""},
                    {"indicator":{"id":"NY.GDP.PCAP.CD","value":"GDP per capita (current US$)"},
                    "country":{"id":"SG","value":"Singapore"},"countryiso3code":"SGP","date":"2024","value":85000.0,"obs_status":""}]]
                    """);
        WorldBankService service = new WorldBankService(worldBank);

        assertThat(service.indicatorSearch(Map.of("query", "GDP per capita")).data().get("results").toString())
                .contains("NY.GDP.PCAP.CD");
        assertThat(service.indicatorData(Map.of("indicator", "NY.GDP.PCAP.CD", "country", "SG", "fromYear", 2024, "toYear", 2025))
                .data().get("values").toString()).contains("Singapore").contains("90000.0");
    }

    private static JsonProvider provider(String json) {
        return (path, query) -> payload(path, json);
    }

    private static ProviderPayload payload(String path, String json) {
        try {
            JsonNode node = JSON.readTree(json);
            return new ProviderPayload(node, URI.create("https://fixture.example" + path),
                    OffsetDateTime.parse("2026-10-01T00:00:00Z"), false);
        }
        catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
