package org.wrj.haifa.ai.utilitymcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wrj.haifa.ai.utilitymcp.mcp.ToolArguments;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityResult;
import org.wrj.haifa.ai.utilitymcp.provider.JsonPostProvider;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ProviderPayload;

public class OsvService {

    private static final Set<String> ECOSYSTEMS = Set.of("MAVEN", "NPM", "PYPI");
    private final JsonPostProvider osv;
    private final JsonProvider osvGet;

    public OsvService(JsonPostProvider osv, JsonProvider osvGet) {
        this.osv = osv;
        this.osvGet = osvGet;
    }

    public UtilityResult query(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String ecosystem = args.enumValue("ecosystem", "", ECOSYSTEMS);
        String packageName = args.requiredString("package", 300);
        String version = args.requiredString("version", 128);
        String osvEcosystem = switch (ecosystem) {
            case "MAVEN" -> "Maven";
            case "NPM" -> "npm";
            case "PYPI" -> "PyPI";
            default -> throw new IllegalStateException("unexpected ecosystem");
        };
        ProviderPayload payload = osv.post("/v1/query", Map.of(
                "version", version,
                "package", Map.of("ecosystem", osvEcosystem, "name", packageName)));
        JsonNode vulns = payload.body().path("vulns");
        List<Map<String, Object>> results = new ArrayList<>();
        if (vulns.isArray()) {
            for (JsonNode vuln : vulns) {
                if (results.size() >= 50) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", vuln.path("id").asText());
                String summary = JsonSupport.optionalText(vuln, "summary");
                if (summary != null) item.put("summary", summary);
                String published = JsonSupport.optionalText(vuln, "published");
                if (published != null) item.put("published", published);
                String modified = JsonSupport.optionalText(vuln, "modified");
                if (modified != null) item.put("modified", modified);
                List<Object> aliases = JsonSupport.scalarArray(vuln.path("aliases"), 20);
                if (!aliases.isEmpty()) item.put("aliases", aliases);
                results.add(item);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ecosystem", ecosystem);
        data.put("package", packageName);
        data.put("version", version);
        data.put("vulnerabilities", results);
        data.put("count", results.size());
        boolean partial = vulns.isArray() && vulns.size() > results.size()
                || !payload.body().path("next_page_token").asText("").isBlank();
        return UtilityResult.external(data, "osv", payload.sourceUri(), payload.retrievedAt(),
                payload.cached(), partial, null, Map.of());
    }

    public UtilityResult get(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String id = args.requiredString("id", 128).trim();
        if (!id.matches("[A-Za-z0-9._:-]+")) {
            throw org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException.invalid("id contains unsupported characters");
        }
        ProviderPayload payload = osvGet.get("/v1/vulns/" + id, Map.of());
        JsonNode vuln = payload.body();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", vuln.path("id").asText(id));
        String summary = JsonSupport.optionalText(vuln, "summary");
        if (summary != null) data.put("summary", truncate(summary, 2_000));
        String details = JsonSupport.optionalText(vuln, "details");
        if (details != null) data.put("details", truncate(details, 12_000));
        String published = JsonSupport.optionalText(vuln, "published");
        if (published != null) data.put("published", published);
        String modified = JsonSupport.optionalText(vuln, "modified");
        if (modified != null) data.put("modified", modified);
        List<Object> aliases = JsonSupport.scalarArray(vuln.path("aliases"), 30);
        if (!aliases.isEmpty()) data.put("aliases", aliases);
        JsonNode refs = vuln.path("references");
        List<Map<String, Object>> references = new ArrayList<>();
        if (refs.isArray()) {
            for (JsonNode ref : refs) {
                if (references.size() >= 30) break;
                String url = JsonSupport.optionalText(ref, "url");
                if (url == null) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("url", url);
                String type = JsonSupport.optionalText(ref, "type");
                if (type != null) item.put("type", type);
                references.add(item);
            }
        }
        if (!references.isEmpty()) data.put("references", references);
        boolean partial = (refs.isArray() && refs.size() > references.size())
                || (summary != null && summary.length() > 2_000)
                || (details != null && details.length() > 12_000)
                || (vuln.path("aliases").isArray() && vuln.path("aliases").size() > aliases.size());
        return UtilityResult.external(data, "osv", payload.sourceUri(), payload.retrievedAt(),
                payload.cached(), partial, null, Map.of());
    }

    private static String truncate(String value, int max) {
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
