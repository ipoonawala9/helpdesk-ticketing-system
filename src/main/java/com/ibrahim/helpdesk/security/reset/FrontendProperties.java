package com.ibrahim.helpdesk.security.reset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the browser app lives, so emailed links point at it.
 *
 * @param baseUrl the frontend's origin, e.g. {@code https://helpdesk-web.pages.dev}
 */
@ConfigurationProperties(prefix = "helpdesk.frontend")
public record FrontendProperties(@DefaultValue("http://localhost:5173") String baseUrl) {

    public String resetLink(String token) {
        return baseUrl().replaceAll("/+$", "") + "/reset-password?token=" + token;
    }
}
