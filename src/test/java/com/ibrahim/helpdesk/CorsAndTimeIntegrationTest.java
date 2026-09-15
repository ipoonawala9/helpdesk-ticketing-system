package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cross-origin access for the browser frontend, and the zone timestamps are in. */
class CorsAndTimeIntegrationTest extends ApiIntegrationTestSupport {

    private static final String FRONTEND = "http://localhost:5173";

    @Test
    @DisplayName("a preflight from the configured frontend origin is allowed for authenticated requests")
    void preflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/tickets")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    @DisplayName("an actual request from the frontend origin carries the allow-origin header")
    void actualRequestFromAllowedOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(asSuperAdmin()).header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
    }

    @Test
    @DisplayName("any other origin is refused")
    void otherOriginRefused() throws Exception {
        mockMvc.perform(options("/api/tickets")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("the application runs in UTC, so zone-less timestamps in responses are UTC")
    void timestampsAreUtc() throws Exception {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");

        long customer = createUser("Tz Customer", "CUSTOMER", createOrganization("Tz Org"));
        String createdAt = JsonPath.read(fetchTicket(createTicket(customer)), "$.createdAt");

        LocalDateTime utcNow = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(Duration.between(LocalDateTime.parse(createdAt), utcNow).abs()).isLessThan(Duration.ofMinutes(1));
    }
}
