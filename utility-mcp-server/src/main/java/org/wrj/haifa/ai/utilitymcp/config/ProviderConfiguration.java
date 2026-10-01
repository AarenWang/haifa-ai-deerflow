package org.wrj.haifa.ai.utilitymcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.wrj.haifa.ai.utilitymcp.provider.JsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ResilientJsonProvider;
import org.wrj.haifa.ai.utilitymcp.provider.JsonPostProvider;
import org.wrj.haifa.ai.utilitymcp.provider.ResilientJsonPostProvider;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class ProviderConfiguration {

    @Bean
    @Qualifier("openMeteoForecast")
    JsonProvider openMeteoForecast(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("open-meteo", properties.getProviders().getOpenMeteo(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("openMeteoGeocoding")
    JsonProvider openMeteoGeocoding(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("open-meteo-geocoding", properties.getProviders().getOpenMeteoGeocoding(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("openMeteoAirQuality")
    JsonProvider openMeteoAirQuality(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("open-meteo-air-quality", properties.getProviders().getOpenMeteoAirQuality(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("frankfurter")
    JsonProvider frankfurter(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("frankfurter", properties.getProviders().getFrankfurter(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("nagerDate")
    JsonProvider nagerDate(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("nager-date", properties.getProviders().getNagerDate(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("wikimedia")
    JsonProvider wikimedia(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("wikimedia", properties.getProviders().getWikimedia(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("crossref")
    JsonProvider crossref(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("crossref", properties.getProviders().getCrossref(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("openAlex")
    JsonProvider openAlex(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("openalex", properties.getProviders().getOpenAlex(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("mavenCentral")
    JsonProvider mavenCentral(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("maven-central", properties.getProviders().getMavenCentral(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("npmRegistry")
    JsonProvider npmRegistry(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("npm-registry", properties.getProviders().getNpmRegistry(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("pypi")
    JsonProvider pypi(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("pypi", properties.getProviders().getPypi(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("osvGet")
    JsonProvider osvGet(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("osv", properties.getProviders().getOsv(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("osv")
    JsonPostProvider osv(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return new ResilientJsonPostProvider(
                "osv", properties.getProviders().getOsv(), objectMapper, meterRegistry, properties.getProxy());
    }

    @Bean
    @Qualifier("googleDns")
    JsonProvider googleDns(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("google-dns", properties.getProviders().getGoogleDns(), properties, objectMapper, meterRegistry);
    }

    @Bean
    @Qualifier("worldBank")
    JsonProvider worldBank(UtilityMcpProperties properties, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        return provider("world-bank", properties.getProviders().getWorldBank(), properties, objectMapper, meterRegistry);
    }

    private static JsonProvider provider(
            String providerId,
            UtilityMcpProperties.Provider provider,
            UtilityMcpProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        return new ResilientJsonProvider(providerId, provider, objectMapper, meterRegistry, properties.getProxy());
    }
}
