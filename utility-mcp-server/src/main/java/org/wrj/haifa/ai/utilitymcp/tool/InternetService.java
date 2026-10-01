package org.wrj.haifa.ai.utilitymcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.IDN;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.wrj.haifa.ai.utilitymcp.mcp.ToolArguments;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityResult;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ProviderPayload;

public class InternetService {

    private static final Set<String> TYPES = Set.of("A", "AAAA", "CNAME", "MX", "TXT", "NS", "CAA", "SRV", "PTR");
    private final JsonProvider dns;

    public InternetService(JsonProvider dns) {
        this.dns = dns;
    }

    public UtilityResult dnsQuery(Map<String, Object> arguments) {
        ToolArguments args = new ToolArguments(arguments);
        String name = dnsName(args.requiredString("name", 253));
        String type = args.enumValue("type", "A", TYPES);
        ProviderPayload payload = dns.get("/resolve", Map.of("name", name, "type", type));
        JsonNode body = payload.body();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("type", type);
        data.put("status", body.path("Status").asInt(-1));
        data.put("authenticatedData", body.path("AD").asBoolean(false));
        data.put("truncated", body.path("TC").asBoolean(false));
        List<Map<String, Object>> answers = new ArrayList<>();
        JsonNode answer = body.path("Answer");
        if (answer.isArray()) {
            for (JsonNode record : answer) {
                if (answers.size() >= 100) break;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", record.path("name").asText(""));
                item.put("type", record.path("type").asInt());
                item.put("ttl", record.path("TTL").asLong());
                item.put("data", record.path("data").asText(""));
                answers.add(item);
            }
        }
        data.put("answers", answers);
        boolean partial = body.path("TC").asBoolean(false) || answer.isArray() && answer.size() > answers.size();
        return UtilityResult.external(data, "google-public-dns", payload.sourceUri(), payload.retrievedAt(),
                payload.cached(), partial, null, Map.of("ttl", "seconds"));
    }

    private static String dnsName(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith(".")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank() || trimmed.length() > 253 || trimmed.contains("..")) {
            throw UtilityToolException.invalid("name is not a valid DNS name");
        }
        try {
            List<String> labels = new ArrayList<>();
            for (String label : trimmed.split("\\.")) {
                if (label.isBlank()) throw UtilityToolException.invalid("name is not a valid DNS name");
                if (label.indexOf('_') >= 0) {
                    if (!label.matches("[A-Za-z0-9_-]{1,63}")) {
                        throw UtilityToolException.invalid("name is not a valid DNS name");
                    }
                    labels.add(label.toLowerCase(Locale.ROOT));
                }
                else {
                    labels.add(IDN.toASCII(label, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT));
                }
            }
            String ascii = String.join(".", labels);
            if (ascii.length() > 253) throw UtilityToolException.invalid("name is not a valid DNS name");
            return ascii;
        }
        catch (IllegalArgumentException ex) {
            throw UtilityToolException.invalid("name is not a valid DNS name");
        }
    }
}
