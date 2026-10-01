package org.wrj.haifa.ai.utilitymcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.wrj.haifa.ai.utilitymcp.mcp.ToolArguments;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityResult;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ProviderPayload;

public class WorldBankService {

    private final JsonProvider worldBank;

    public WorldBankService(JsonProvider worldBank) {
        this.worldBank = worldBank;
    }

    public UtilityResult indicatorSearch(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String query = safeSearch(args.requiredString("query", 200));
        int limit = args.intValue("limit", 10, 1, 50);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        ProviderPayload payload = worldBank.get("/v2/sources/2/search/" + encodedQuery,
                Map.of("format", "json", "per_page", Math.max(limit, 20)));
        JsonNode sources = payload.body().path("source");
        List<Map<String, Object>> results = new ArrayList<>();
        if (sources.isArray()) {
            for (JsonNode source : sources) {
                JsonNode concepts = source.path("concept");
                if (!concepts.isArray()) continue;
                for (JsonNode concept : concepts) {
                    if (!"Series".equalsIgnoreCase(concept.path("id").asText())) continue;
                    JsonNode variables = concept.path("variable");
                    if (!variables.isArray()) continue;
                    for (JsonNode variable : variables) {
                        if (results.size() >= limit) break;
                        String id = variable.path("id").asText("").trim();
                        if (id.isBlank()) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("indicator", id);
                        String name = JsonSupport.optionalText(variable, "name");
                        if (name != null && !name.isBlank()) item.put("name", name.trim());
                        String snippet = firstMetadataValue(variable.path("metatype"));
                        if (snippet != null) item.put("match", truncate(snippet, 1_000));
                        results.add(item);
                    }
                    if (results.size() >= limit) break;
                }
                if (results.size() >= limit) break;
            }
        }
        long total = payload.body().path("total").asLong(results.size());
        return UtilityResult.external(Map.of("results", results, "source", "World Development Indicators"),
                "world-bank", payload.sourceUri(), payload.retrievedAt(), payload.cached(), total > results.size(), null, Map.of());
    }

    public UtilityResult indicatorData(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String indicator = indicator(args.requiredString("indicator", 100));
        String country = country(args.requiredString("country", 64));
        int fromYear = args.intValue("fromYear", 2000, 1000, 2100);
        int toYear = args.intValue("toYear", 2100, 1000, 2100);
        int limit = args.intValue("limit", 100, 1, 500);
        if (fromYear > toYear) throw UtilityToolException.invalid("fromYear must not exceed toYear");
        ProviderPayload payload = worldBank.get("/v2/country/" + country + "/indicator/" + indicator,
                Map.of("format", "json", "date", fromYear + ":" + toYear, "per_page", limit));
        JsonNode body = payload.body();
        if (!body.isArray() || body.size() < 2) throw JsonSupport.malformed("World Bank data response is not an array pair");
        JsonNode rows = body.get(1);
        List<Map<String, Object>> values = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (values.size() >= limit) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("year", row.path("date").asText());
                JsonNode value = row.get("value");
                item.put("value", value == null || value.isNull() ? null : (value.isNumber() ? value.numberValue() : value.asText()));
                item.put("country", row.path("country").path("value").asText());
                String iso3 = JsonSupport.optionalText(row, "countryiso3code");
                if (iso3 != null) item.put("countryIso3", iso3);
                String status = JsonSupport.optionalText(row, "obs_status");
                if (status != null && !status.isBlank()) item.put("observationStatus", status);
                values.add(item);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("indicator", indicator);
        if (!values.isEmpty()) data.put("indicatorName", rows.get(0).path("indicator").path("value").asText());
        data.put("country", country);
        data.put("values", values);
        long total = body.get(0).path("total").asLong(rows != null && rows.isArray() ? rows.size() : 0);
        return UtilityResult.external(data, "world-bank", payload.sourceUri(), payload.retrievedAt(), payload.cached(),
                total > values.size(), null, Map.of());
    }

    private static String safeSearch(String value) {
        String query = value.trim();
        if (query.contains("/") || query.contains("\\") || query.contains("..") || query.contains("?") || query.contains("#")) {
            throw UtilityToolException.invalid("query contains unsupported path characters");
        }
        return query;
    }

    private static String indicator(String value) {
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9._-]+")) throw UtilityToolException.invalid("indicator has unsupported characters");
        return normalized;
    }

    private static String country(String value) {
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]+")) throw UtilityToolException.invalid("country must be a World Bank country or aggregate code");
        return normalized;
    }

    private static String firstMetadataValue(JsonNode metatypes) {
        if (!metatypes.isArray()) return null;
        for (JsonNode metatype : metatypes) {
            String value = JsonSupport.optionalText(metatype, "value");
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String truncate(String value, int max) {
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
