package com.ibrahim.helpdesk;

import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login and access-token handling through the real security filter chain.
 */
class AuthIntegrationTest extends ApiIntegrationTestSupport {

    private long acmeId;
    private long customerId;
    private String customerEmail;

    @BeforeEach
    void setUp() throws Exception {
        acmeId = createOrganization("Acme Ltd");
        customerId = createUser("Dana Customer", "CUSTOMER", acmeId);
        customerEmail = userRepository.findById(customerId).orElseThrow().getEmail();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // ----- login ----------------------------------------------------------------

    @Test
    @DisplayName("a correct email and password return a bearer token that authenticates the user")
    void successfulLogin() throws Exception {
        String body = login(customerEmail, USER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.user.id").value((int) customerId))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(USER_PASSWORD).doesNotContain("{bcrypt}");

        String token = JsonPath.read(body, "$.accessToken");
        me(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) customerId))
                .andExpect(jsonPath("$.email").value(customerEmail));
    }

    @Test
    @DisplayName("the token carries only issuer, subject and timestamps; no role, email or password")
    void tokenContainsNoSensitiveClaims() throws Exception {
        String token = JsonPath.read(login(customerEmail, USER_PASSWORD).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertThat(JsonPath.<String>read(payload, "$.sub")).isEqualTo(String.valueOf(customerId));
        assertThat(JsonPath.<String>read(payload, "$.iss")).isEqualTo("helpdesk");
        assertThat(payload).doesNotContain("CUSTOMER", customerEmail, "password");
    }

    @Test
    @DisplayName("login ignores letter case and surrounding spaces in the email")
    void emailIsCaseInsensitive() throws Exception {
        login("  " + customerEmail.toUpperCase() + " ", USER_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong password, unknown email and inactive account all get the same 401")
    void failuresAreIndistinguishable() throws Exception {
        String wrongPassword = login(customerEmail, "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login("nobody@example.test", USER_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        User customer = userRepository.findById(customerId).orElseThrow();
        customer.setActive(false);
        userRepository.save(customer);
        String inactive = login(customerEmail, USER_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        for (String body : new String[] {wrongPassword, unknownEmail, inactive}) {
            assertThat(JsonPath.<String>read(body, "$.message")).isEqualTo("Invalid email or password");
            assertThat(JsonPath.<String>read(body, "$.path")).isEqualTo("/api/auth/login");
        }
    }

    @Test
    @DisplayName("login validates that email and password are present")
    void loginValidatesBody() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Email is required"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"));
    }

    @Test
    @DisplayName("passwords are stored as bcrypt hashes, never as the password itself")
    void passwordsAreHashedAtRest() {
        String stored = userRepository.findById(customerId).orElseThrow().getPassword();

        assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(USER_PASSWORD);
    }

    // ----- token verification -----------------------------------------------------

    @Test
    @DisplayName("no token is a 401")
    void missingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredToken() throws Exception {
        clock.advance(Duration.ofHours(-2));
        String expired = tokenFor(customerId);
        clock.reset();

        me(expired)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    @Test
    @DisplayName("a token signed with a different key is rejected, even with valid claims")
    void forgedSignature() throws Exception {
        me(signed("an-attackers-own-signing-key-0123456789abcdef", "helpdesk", customerId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a correctly signed token from a different issuer is rejected")
    void wrongIssuer() throws Exception {
        me(signed("test-only-signing-key-not-a-real-secret-0123456789", "someone-else", customerId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unsigned token (alg none) is rejected")
    void unsignedToken() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"iss\":\"helpdesk\",\"sub\":\"" + customerId
                + "\",\"exp\":" + Instant.now().plusSeconds(3600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));

        me(header + "." + payload + ".").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a deactivated user's existing token stops working on the next request")
    void deactivationIsImmediate() throws Exception {
        String token = tokenFor(customerId);
        me(token).andExpect(status().isOk());

        User customer = userRepository.findById(customerId).orElseThrow();
        customer.setActive(false);
        userRepository.save(customer);

        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a role change applies to existing tokens immediately, because roles are read from the database")
    void roleChangeIsImmediate() throws Exception {
        long agentId = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);
        String token = tokenFor(agentId);
        long ticketId = createTicket(customerId);

        User agent = userRepository.findById(agentId).orElseThrow();
        agent.setRole(UserRole.CUSTOMER);
        userRepository.save(agent);

        mockMvc.perform(post("/api/tickets/{id}/start", ticketId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the bootstrap super admin from configuration can log in")
    void bootstrapSuperAdminCanLogIn() throws Exception {
        login(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.user.organization").doesNotExist());
    }

    @Test
    @DisplayName("the OpenAPI document is public, so the API can be explored before logging in")
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
    }

    private static String signed(String secret, String issuer, long subject) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(subject))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
