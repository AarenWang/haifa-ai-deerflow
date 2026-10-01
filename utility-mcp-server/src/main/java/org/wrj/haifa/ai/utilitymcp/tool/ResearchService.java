package org.wrj.haifa.ai.utilitymcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.wrj.haifa.ai.utilitymcp.mcp.ToolArguments;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityErrorCode;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityResult;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ProviderPayload;

public class ResearchService {

    private final JsonProvider crossref;
    private final JsonProvider openAlex;

    public ResearchService(JsonProvider crossref, JsonProvider openAlex) {
        this.crossref = crossref;
        this.openAlex = openAlex;
    }

    public UtilityResult search(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String query = args.requiredString("query", 500);
        int fromYear = args.intValue("fromYear", 1900, 1000, 2100);
        int toYear = args.intValue("toYear", 2100, 1000, 2100);
        int limit = args.intValue("limit", 10, 1, 50);
        if (fromYear > toYear) throw UtilityToolException.invalid("fromYear must not exceed toYear");

        Map<String, Object> queryParams = new LinkedHashMap<>();
        queryParams.put("query.bibliographic", query);
        queryParams.put("rows", limit);
        if (fromYear != 1900 || toYear != 2100) {
            queryParams.put("filter", "from-pub-date:" + fromYear + "-01-01,until-pub-date:" + toYear + "-12-31");
        }
        ProviderPayload payload = crossref.get("/works", queryParams);
        JsonNode items = JsonSupport.required(JsonSupport.required(payload.body(), "message"), "items");
        if (!items.isArray()) throw JsonSupport.malformed("Crossref items are not an array");
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode item : items) {
            if (results.size() >= limit) break;
            results.add(paper(item));
        }
        long total = payload.body().path("message").path("total-results").asLong(items.size());
        return UtilityResult.external(Map.of("results", results), "crossref", payload.sourceUri(),
                payload.retrievedAt(), payload.cached(), total > results.size(), null, Map.of());
    }

    public UtilityResult get(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String doi = doi(args.requiredString("doi", 300));
        ProviderPayload payload = crossref.get("/works/" + doi, Map.of());
        JsonNode message = JsonSupport.required(payload.body(), "message");
        Map<String, Object> data = paper(message);
        return UtilityResult.external(data, "crossref", payload.sourceUri(), payload.retrievedAt(),
                payload.cached(), false, null, Map.of());
    }

    public UtilityResult doiLookup(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String title = args.requiredString("title", 500);
        String author = args.optionalString("author", null, 200);
        int year = args.intValue("year", 0, 0, 2100);
        int limit = args.intValue("limit", 5, 1, 20);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("query.bibliographic", title);
        if (author != null) query.put("query.author", author);
        query.put("rows", limit);
        if (year > 0) query.put("filter", "from-pub-date:" + year + "-01-01,until-pub-date:" + year + "-12-31");
        ProviderPayload payload = crossref.get("/works", query);
        JsonNode items = JsonSupport.required(JsonSupport.required(payload.body(), "message"), "items");
        if (!items.isArray()) throw JsonSupport.malformed("Crossref items are not an array");
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (JsonNode item : items) {
            if (candidates.size() >= limit) break;
            Map<String, Object> candidate = paper(item);
            if (candidate.containsKey("doi")) candidates.add(candidate);
        }
        long total = payload.body().path("message").path("total-results").asLong(items.size());
        return UtilityResult.external(Map.of("candidates", candidates), "crossref", payload.sourceUri(),
                payload.retrievedAt(), payload.cached(), total > candidates.size(), null, Map.of());
    }

    public UtilityResult authorSearch(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String query = args.requiredString("query", 300);
        int limit = args.intValue("limit", 10, 1, 50);
        ProviderPayload payload = openAlex.get("/authors", Map.of("search", query, "per_page", limit));
        JsonNode rows = payload.body().path("results");
        if (!rows.isArray()) throw JsonSupport.malformed("OpenAlex author results are missing");
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode row : rows) {
            if (results.size() >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            String id = JsonSupport.optionalText(row, "id");
            if (id != null) item.put("openAlexId", id);
            String name = JsonSupport.optionalText(row, "display_name");
            if (name != null) item.put("name", name);
            String orcid = JsonSupport.optionalText(row, "orcid");
            if (orcid != null) item.put("orcid", orcid);
            if (row.has("works_count")) item.put("worksCount", row.path("works_count").asLong());
            if (row.has("cited_by_count")) item.put("citationCount", row.path("cited_by_count").asLong());
            JsonNode institutions = row.path("last_known_institutions");
            if (institutions.isArray()) {
                List<String> names = new ArrayList<>();
                for (JsonNode institution : institutions) {
                    if (names.size() >= 5) break;
                    String institutionName = JsonSupport.optionalText(institution, "display_name");
                    if (institutionName != null && !institutionName.isBlank()) names.add(institutionName);
                }
                if (!names.isEmpty()) item.put("lastKnownInstitutions", names);
            }
            results.add(item);
        }
        long total = payload.body().path("meta").path("count").asLong(rows.size());
        return UtilityResult.external(Map.of("results", results), "openalex", payload.sourceUri(),
                payload.retrievedAt(), payload.cached(), total > results.size(), null, Map.of());
    }

    private static Map<String, Object> paper(JsonNode item) {
        Map<String, Object> result = new LinkedHashMap<>();
        String doi = JsonSupport.optionalText(item, "DOI");
        if (doi != null) result.put("doi", doi);
        String title = firstText(item.path("title"));
        if (title != null) result.put("title", title);
        String type = JsonSupport.optionalText(item, "type");
        if (type != null) result.put("type", type);
        String publisher = JsonSupport.optionalText(item, "publisher");
        if (publisher != null) result.put("publisher", publisher);
        String container = firstText(item.path("container-title"));
        if (container != null) result.put("containerTitle", container);
        String published = dateParts(item.path("published-print"));
        if (published == null) published = dateParts(item.path("published-online"));
        if (published == null) published = dateParts(item.path("published"));
        if (published != null) result.put("published", published);
        if (item.has("is-referenced-by-count")) result.put("citationCount", item.path("is-referenced-by-count").asInt());
        String url = JsonSupport.optionalText(item, "URL");
        if (url != null) result.put("url", url);
        JsonNode authors = item.path("author");
        if (authors.isArray()) {
            List<String> names = new ArrayList<>();
            for (JsonNode author : authors) {
                if (names.size() >= 20) break;
                String given = JsonSupport.optionalText(author, "given");
                String family = JsonSupport.optionalText(author, "family");
                String name = ((given == null ? "" : given + " ") + (family == null ? "" : family)).trim();
                if (!name.isBlank()) names.add(name);
            }
            if (!names.isEmpty()) result.put("authors", names);
        }
        return result;
    }

    private static String firstText(JsonNode array) {
        return array != null && array.isArray() && !array.isEmpty() && array.get(0).isValueNode()
                ? array.get(0).asText() : null;
    }

    private static String dateParts(JsonNode published) {
        JsonNode parts = published == null ? null : published.path("date-parts");
        if (parts == null || !parts.isArray() || parts.isEmpty() || !parts.get(0).isArray()) return null;
        JsonNode first = parts.get(0);
        if (first.isEmpty()) return null;
        int year = first.get(0).asInt();
        if (year <= 0) return null;
        if (first.size() == 1) return String.format("%04d", year);
        int month = first.get(1).asInt();
        if (first.size() == 2) return String.format("%04d-%02d", year, month);
        return String.format("%04d-%02d-%02d", year, month, first.get(2).asInt());
    }

    private static String doi(String value) {
        String normalized = value.trim();
        if (!normalized.matches("(?i)^10\\.\\d{4,9}/[-._;()/:A-Z0-9]+$")) {
            throw new UtilityToolException(UtilityErrorCode.INVALID_ARGUMENT, "doi is not a valid DOI", false);
        }
        return normalized;
    }
}
