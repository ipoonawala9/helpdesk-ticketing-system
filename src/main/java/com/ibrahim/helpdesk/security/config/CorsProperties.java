package com.ibrahim.helpdesk.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Browser origins allowed to call the API from another site, such as the
 * separately hosted frontend. Bound from {@code helpdesk.cors.allowed-origins}
 * as a comma-separated list; empty means no cross-origin access.
 */
@ConfigurationProperties(prefix = "helpdesk.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "helpdesk.cors.allowed-origins must list explicit origins, not *");
        }
    }
}
