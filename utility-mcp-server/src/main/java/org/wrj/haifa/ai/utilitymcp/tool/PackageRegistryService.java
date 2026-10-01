package org.wrj.haifa.ai.utilitymcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wrj.haifa.ai.utilitymcp.mcp.ToolArguments;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityErrorCode;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityResult;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ProviderPayload;

public class PackageRegistryService {

    private static final Set<String> ALL_ECOSYSTEMS = Set.of("MAVEN", "NPM", "PYPI");
    private static final Set<String> SEARCH_ECOSYSTEMS = Set.of("MAVEN", "NPM");
    private final JsonProvider maven;
    private final JsonProvider npm;
    private final JsonProvider pypi;

    public PackageRegistryService(JsonProvider maven, JsonProvider npm, JsonProvider pypi) {
        this.maven = maven;
        this.npm = npm;
        this.pypi = pypi;
    }

    public UtilityResult search(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String ecosystem = args.enumValue("ecosystem", "", SEARCH_ECOSYSTEMS);
        String query = args.requiredString("query", 300);
        int limit = args.intValue("limit", 10, 1, 50);
        return ecosystem.equals("MAVEN") ? searchMaven(query, limit) : searchNpm(query, limit);
    }

    public UtilityResult info(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String ecosystem = args.enumValue("ecosystem", "", ALL_ECOSYSTEMS);
        String packageName = args.requiredString("package", 300);
        return switch (ecosystem) {
            case "MAVEN" -> mavenInfo(packageName);
            case "NPM" -> npmInfo(packageName);
            case "PYPI" -> pypiInfo(packageName);
            default -> throw new IllegalStateException("unexpected ecosystem");
        };
    }

    public UtilityResult versions(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String ecosystem = args.enumValue("ecosystem", "", ALL_ECOSYSTEMS);
        String packageName = args.requiredString("package", 300);
        int limit = args.intValue("limit", 50, 1, 100);
        return switch (ecosystem) {
            case "MAVEN" -> mavenVersions(packageName, limit);
            case "NPM" -> npmVersions(packageName, limit);
            case "PYPI" -> pypiVersions(packageName, limit);
            default -> throw new IllegalStateException("unexpected ecosystem");
        };
    }

    public UtilityResult latest(Map<String, Object> arguments) {
        UtilityResult info = info(arguments);
        Object latest = info.data().get("latestVersion");
        if (latest == null) {
            throw new UtilityToolException(UtilityErrorCode.UPSTREAM_UNAVAILABLE,
                    "Package registry did not report a latest version", true);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ecosystem", info.data().get("ecosystem"));
        data.put("package", info.data().get("package"));
        data.put("latestVersion", latest);
        return new UtilityResult(data, info.meta());
    }

    private UtilityResult searchMaven(String query, int limit) {
        ProviderPayload payload = maven.get("/solrsearch/select", Map.of("q", query, "rows", limit, "wt", "json"));
        JsonNode docs = payload.body().path("response").path("docs");
        if (!docs.isArray()) throw JsonSupport.malformed("Maven search docs are missing");
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode doc : docs) {
            if (results.size() >= limit) break;
            Map<String, Object> item = new LinkedHashMap<>();
            String group = JsonSupport.optionalText(doc, "g");
            String artifact = JsonSupport.optionalText(doc, "a");
            if (group != null && artifact != null) item.put("package", group + ":" + artifact);
            String latest = JsonSupport.optionalText(doc, "latestVersion");
            if (latest != null) item.put("latestVersion", latest);
            if (doc.has("versionCount")) item.put("versionCount", doc.path("versionCount").asInt());
            results.add(item);
        }
        long total = payload.body().path("response").path("numFound").asLong(docs.size());
        return UtilityResult.external(Map.of("ecosystem", "MAVEN", "results", results), "maven-central",
                payload.sourceUri(), payload.retrievedAt(), payload.cached(), total > results.size(), null, Map.of());
    }

    private UtilityResult searchNpm(String query, int limit) {
        ProviderPayload payload = npm.get("/-/v1/search", Map.of("text", query, "size", limit));
        JsonNode objects = payload.body().path("objects");
        if (!objects.isArray()) throw JsonSupport.malformed("npm search objects are missing");
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode object : objects) {
            if (results.size() >= limit) break;
            JsonNode p = object.path("package");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("package", p.path("name").asText());
            item.put("latestVersion", p.path("version").asText());
            String description = JsonSupport.optionalText(p, "description");
            if (description != null) item.put("description", truncate(description, 1_000));
            if (object.has("searchScore")) item.put("searchScore", object.path("searchScore").asDouble());
            results.add(item);
        }
        long total = payload.body().path("total").asLong(objects.size());
        return UtilityResult.external(Map.of("ecosystem", "NPM", "results", results), "npm",
                payload.sourceUri(), payload.retrievedAt(), payload.cached(), total > results.size(), null, Map.of());
    }

    private UtilityResult mavenInfo(String packageName) {
        MavenCoordinate coordinate = mavenCoordinate(packageName);
        String q = "g:\"" + coordinate.group() + "\" AND a:\"" + coordinate.artifact() + "\"";
        ProviderPayload payload = maven.get("/solrsearch/select", Map.of("q", q, "rows", 1, "wt", "json"));
        JsonNode docs = payload.body().path("response").path("docs");
        if (!docs.isArray() || docs.isEmpty()) throw notFound("Maven package not found");
        JsonNode doc = docs.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ecosystem", "MAVEN");
        data.put("package", coordinate.group() + ":" + coordinate.artifact());
        data.put("groupId", coordinate.group());
        data.put("artifactId", coordinate.artifact());
        String latest = JsonSupport.optionalText(doc, "latestVersion");
        if (latest != null) data.put("latestVersion", latest);
        if (doc.has("versionCount")) data.put("versionCount", doc.path("versionCount").asInt());
        return UtilityResult.external(data, "maven-central", payload.sourceUri(), payload.retrievedAt(),
                payload.cached(), false, null, Map.of());
    }

    private UtilityResult mavenVersions(String packageName, int limit) {
        MavenCoordinate coordinate = mavenCoordinate(packageName);
        String q = "g:\"" + coordinate.group() + "\" AND a:\"" + coordinate.artifact() + "\"";
        ProviderPayload payload = maven.get("/solrsearch/select", Map.of(
                "q", q, "core", "gav", "rows", limit, "wt", "json"));
        JsonNode docs = payload.body().path("response").path("docs");
        if (!docs.isArray()) throw JsonSupport.malformed("Maven version docs are missing");
        List<String> versions = new ArrayList<>();
        for (JsonNode doc : docs) {
            if (versions.size() >= limit) break;
            String version = JsonSupport.optionalText(doc, "v");
            if (version != null) versions.add(version);
        }
        long total = payload.body().path("response").path("numFound").asLong(docs.size());
        return UtilityResult.external(Map.of("ecosystem", "MAVEN", "package", packageName, "versions", versions),
                "maven-central", payload.sourceUri(), payload.retrievedAt(), payload.cached(), total > versions.size(), null, Map.of());
    }

    private UtilityResult npmInfo(String packageName) {
        String name = npmName(packageName);
        ProviderPayload payload = npm.get("/" + name, Map.of());
        JsonNode body = payload.body();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ecosystem", "NPM");
        data.put("package", body.path("name").asText(name));
        String description = JsonSupport.optionalText(body, "description");
        if (description != null) data.put("description", truncate(description, 2_000));
        String latest = body.path("dist-tags").path("latest").asText(null);
        if (latest != null) data.put("latestVersion", latest);
        String homepage = JsonSupport.optionalText(body, "homepage");
        if (homepage != null) data.put("homepage", homepage);
        String repository = repositoryUrl(body.path("repository"));
        if (repository != null) data.put("repository", repository);
        return UtilityResult.external(data, "npm", payload.sourceUri(), payload.retrievedAt(), payload.cached(), false, null, Map.of());
    }

    private UtilityResult npmVersions(String packageName, int limit) {
        String name = npmName(packageName);
        ProviderPayload payload = npm.get("/" + name, Map.of());
        List<String> versions = objectKeys(payload.body().path("versions"), limit);
        return UtilityResult.external(Map.of("ecosystem", "NPM", "package", name, "versions", versions), "npm",
                payload.sourceUri(), payload.retrievedAt(), payload.cached(),
                payload.body().path("versions").size() > versions.size(), null, Map.of());
    }

    private UtilityResult pypiInfo(String packageName) {
        String name = simplePackageName(packageName);
        ProviderPayload payload = pypi.get("/pypi/" + name + "/json", Map.of());
        JsonNode info = payload.body().path("info");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ecosystem", "PYPI");
        data.put("package", info.path("name").asText(name));
        String description = JsonSupport.optionalText(info, "summary");
        if (description != null) data.put("description", truncate(description, 2_000));
        String latest = JsonSupport.optionalText(info, "version");
        if (latest != null) data.put("latestVersion", latest);
        String homepage = JsonSupport.optionalText(info, "home_page");
        if (homepage != null && !homepage.isBlank()) data.put("homepage", homepage);
        return UtilityResult.external(data, "pypi", payload.sourceUri(), payload.retrievedAt(), payload.cached(), false, null, Map.of());
    }

    private UtilityResult pypiVersions(String packageName, int limit) {
        String name = simplePackageName(packageName);
        ProviderPayload payload = pypi.get("/pypi/" + name + "/json", Map.of());
        List<String> versions = objectKeys(payload.body().path("releases"), limit);
        return UtilityResult.external(Map.of("ecosystem", "PYPI", "package", name, "versions", versions), "pypi",
                payload.sourceUri(), payload.retrievedAt(), payload.cached(),
                payload.body().path("releases").size() > versions.size(), null, Map.of());
    }

    private static MavenCoordinate mavenCoordinate(String packageName) {
        String[] parts = packageName.split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_.-]+") || !parts[1].matches("[A-Za-z0-9_.-]+")) {
            throw UtilityToolException.invalid("Maven package must use groupId:artifactId");
        }
        return new MavenCoordinate(parts[0], parts[1]);
    }

    private static String npmName(String value) {
        String name = value.trim();
        if (!name.matches("(@[A-Za-z0-9._-]+/)?[A-Za-z0-9._-]+")) {
            throw UtilityToolException.invalid("package is not a valid npm package name");
        }
        return name;
    }

    private static String simplePackageName(String value) {
        String name = value.trim();
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw UtilityToolException.invalid("package contains unsupported characters");
        }
        return name;
    }

    private static List<String> objectKeys(JsonNode object, int limit) {
        if (!object.isObject()) return List.of();
        List<String> values = new ArrayList<>();
        object.fieldNames().forEachRemaining(name -> {
            if (values.size() < limit) values.add(name);
        });
        return values;
    }

    private static String repositoryUrl(JsonNode repository) {
        if (repository == null || repository.isMissingNode() || repository.isNull()) return null;
        if (repository.isTextual()) return repository.asText();
        return JsonSupport.optionalText(repository, "url");
    }

    private static UtilityToolException notFound(String message) {
        return new UtilityToolException(UtilityErrorCode.UPSTREAM_UNAVAILABLE, message, false);
    }

    private static String truncate(String value, int max) {
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private record MavenCoordinate(String group, String artifact) {}
}
