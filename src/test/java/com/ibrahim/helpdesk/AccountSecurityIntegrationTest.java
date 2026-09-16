package com.ibrahim.helpdesk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Password guessing limits, changing your own password, deactivating
 * accounts, and the public health endpoint, through the real filter chain.
 */
class AccountSecurityIntegrationTest extends ApiIntegrationTestSupport {

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

    private ResultActions changePassword(long userId, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/auth/password")
                .with(as(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}
                        """.formatted(current, next)));
    }

    @Nested
    @DisplayName("Password guessing")
    class Guessing {

        @Test
        @DisplayName("after 5 wrong passwords, sign-in is refused with 429 even with the right password, until the window passes")
        void blocksAfterRepeatedFailures() throws Exception {
            clock.advance(Duration.ZERO); // freeze time so the reported wait is exact
            for (int attempt = 1; attempt <= 5; attempt++) {
                login(customerEmail, "wrong-password-" + attempt).andExpect(status().isUnauthorized());
            }

            login(customerEmail, USER_PASSWORD)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                    .andExpect(jsonPath("$.message").value("Too many failed sign-in attempts. Try again in 15 minutes."));

            clock.advance(Duration.ofMinutes(15));

            login(customerEmail, USER_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("the limit is per email, case-insensitive, and other accounts are unaffected")
        void limitIsPerEmail() throws Exception {
            long otherId = createUser("Kim Customer", "CUSTOMER", acmeId);
            String otherEmail = userRepository.findById(otherId).orElseThrow().getEmail();

            for (int attempt = 0; attempt < 5; attempt++) {
                login(attempt % 2 == 0 ? customerEmail.toUpperCase() : customerEmail, "wrong").andExpect(status().isUnauthorized());
            }

            login(customerEmail, USER_PASSWORD).andExpect(status().isTooManyRequests());
            login(otherEmail, USER_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("a successful sign-in clears earlier failures")
        void successResetsCount() throws Exception {
            for (int attempt = 0; attempt < 4; attempt++) {
                login(customerEmail, "wrong").andExpect(status().isUnauthorized());
            }
            login(customerEmail, USER_PASSWORD).andExpect(status().isOk());

            for (int attempt = 0; attempt < 4; attempt++) {
                login(customerEmail, "wrong").andExpect(status().isUnauthorized());
            }
            login(customerEmail, USER_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("failures older than the window no longer count")
        void failuresExpire() throws Exception {
            for (int attempt = 0; attempt < 4; attempt++) {
                login(customerEmail, "wrong").andExpect(status().isUnauthorized());
            }
            clock.advance(Duration.ofMinutes(16));

            login(customerEmail, "wrong").andExpect(status().isUnauthorized());
            login(customerEmail, USER_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("unknown emails are limited the same way, so the limit reveals nothing")
        void unknownEmailsAlsoLimited() throws Exception {
            // A unique address: limiter state is shared by every test in the context.
            String unknown = uniqueEmail("nobody");
            for (int attempt = 0; attempt < 5; attempt++) {
                login(unknown, "wrong").andExpect(status().isUnauthorized());
            }
            login(unknown, "wrong").andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("Changing your own password")
    class ChangingPassword {

        @Test
        @DisplayName("with the right current password, the new one works and the old one stops working")
        void changesPassword() throws Exception {
            changePassword(customerId, USER_PASSWORD, "a-brand-new-password").andExpect(status().isNoContent());

            login(customerEmail, "a-brand-new-password").andExpect(status().isOk());
            login(customerEmail, USER_PASSWORD).andExpect(status().isUnauthorized());
            assertThat(userRepository.findById(customerId).orElseThrow().getPassword()).startsWith("{bcrypt}");
        }

        @Test
        @DisplayName("a wrong current password is a field error, not a 401 that would sign the user out")
        void wrongCurrentPassword() throws Exception {
            changePassword(customerId, "not-my-password", "a-brand-new-password")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.currentPassword").value("Current password is incorrect"));

            login(customerEmail, USER_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("the new password must be valid and different from the current one")
        void validatesNewPassword() throws Exception {
            changePassword(customerId, USER_PASSWORD, "short")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").value("New password must be between 8 and 100 characters"));
            changePassword(customerId, USER_PASSWORD, USER_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").value("New password must be different from the current one"));
        }

        @Test
        @DisplayName("wrong current passwords count towards the guessing limit, so a stolen token cannot guess the password")
        void guessingThroughChangePasswordIsLimited() throws Exception {
            for (int attempt = 0; attempt < 5; attempt++) {
                changePassword(customerId, "guess-" + attempt, "a-brand-new-password").andExpect(status().isBadRequest());
            }
            changePassword(customerId, USER_PASSWORD, "a-brand-new-password").andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("requires authentication")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/auth/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"x\",\"newPassword\":\"yyyyyyyy\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Deactivating accounts")
    class Deactivation {

        private long adminId;
        private long agentId;

        @BeforeEach
        void staff() throws Exception {
            adminId = createUser("Alex Admin", "ORG_ADMIN", acmeId);
            agentId = createUser("Sam Agent", "SUPPORT_AGENT", acmeId);
        }

        private ResultActions deactivate(long targetId, long actorId) throws Exception {
            return mockMvc.perform(post("/api/users/{id}/deactivate", targetId).with(as(actorId)));
        }

        private ResultActions activate(long targetId, long actorId) throws Exception {
            return mockMvc.perform(post("/api/users/{id}/activate", targetId).with(as(actorId)));
        }

        @Test
        @DisplayName("an org admin deactivates an agent: sign-in is refused and their existing token stops working at once")
        void deactivationRevokesAccess() throws Exception {
            String agentEmail = userRepository.findById(agentId).orElseThrow().getEmail();
            String existingToken = tokenFor(agentId);

            deactivate(agentId, adminId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));

            login(agentEmail, USER_PASSWORD).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/auth/me").with(bearer(existingToken))).andExpect(status().isUnauthorized());

            activate(agentId, adminId).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
            login(agentEmail, USER_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("an org admin cannot deactivate another admin, anyone outside their organization, or themselves")
        void orgAdminLimits() throws Exception {
            long otherAdminId = createUser("Pat Admin", "ORG_ADMIN", acmeId);
            long globexAgentId = createUser("Gil Agent", "SUPPORT_AGENT", createOrganization("Globex Corp"));

            deactivate(otherAdminId, adminId).andExpect(status().isForbidden());
            deactivate(globexAgentId, adminId).andExpect(status().isNotFound());
            deactivate(adminId, adminId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You cannot change whether your own account is active"));
        }

        @Test
        @DisplayName("agents and customers cannot deactivate anyone")
        void nonAdminsForbidden() throws Exception {
            deactivate(customerId, agentId).andExpect(status().isForbidden());
            deactivate(agentId, customerId).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a super admin can deactivate an org admin in any organization, but not themselves")
        void superAdmin() throws Exception {
            mockMvc.perform(post("/api/users/{id}/deactivate", adminId).with(asSuperAdmin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
            mockMvc.perform(post("/api/users/{id}/deactivate", superAdminId()).with(asSuperAdmin()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Health endpoint")
    class Health {

        @Test
        @DisplayName("liveness and overall health are public and reveal no details")
        void publicHealth() throws Exception {
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }

        @Test
        @DisplayName("no other actuator endpoint is reachable")
        void otherEndpointsHidden() throws Exception {
            for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump"}) {
                int status = mockMvc.perform(get(path).with(asSuperAdmin())).andReturn().getResponse().getStatus();
                assertThat(status).as(path).isEqualTo(404);
            }
            mockMvc.perform(get("/actuator/env"))
                    .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(401), org.hamcrest.Matchers.is(404))));
        }
    }
}
