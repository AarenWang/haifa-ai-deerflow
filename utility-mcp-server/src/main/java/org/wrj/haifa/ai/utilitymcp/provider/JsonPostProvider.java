package org.wrj.haifa.ai.utilitymcp.provider;

import java.util.Map;

@FunctionalInterface
public interface JsonPostProvider {
    ProviderPayload post(String path, Map<String, ?> body);
}
