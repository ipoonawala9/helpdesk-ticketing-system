package com.ibrahim.helpdesk;

import com.ibrahim.helpdesk.security.reset.PasswordResetLinkSender;
import com.ibrahim.helpdesk.security.reset.PasswordResetService;
import com.ibrahim.helpdesk.security.reset.PasswordResetTokenRepository;
import com.ibrahim.helpdesk.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-service password reset, end to end through the real filter chain. */
@Import(PasswordResetIntegrationTest.RecordingSenderConfig.class)
class PasswordResetIntegrationTest extends ApiIntegrationTestSupport {

    /** Stands in for the mail server: keeps the links that would have been sent. */
    record SentLink(String email, String link) {
    }

    static class RecordingSender implements PasswordResetLinkSender {
        final List<SentLink> sent = new ArrayList<>();

        @Override
        public void send(User user, String link) {
            sent.add(new SentLink(user.getEmail(), link));
        }
    }

    @TestConfiguration
    static class RecordingSenderConfig {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    @Autowired
    private RecordingSender sender;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    private long customerId;
    private String customerEmail;

    @BeforeEach
    void setUp() throws Exception {
        sender.sent.clear();
        customerId = createUser("Dana Customer", "CUSTOMER", createOrganization("Acme Ltd"));
        customerEmail = userRepository.findById(customerId).orElseThrow().getEmail();
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(email)));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","newPassword":"%s"}
                        """.formatted(token, newPassword)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private String tokenFromLastLink() {
        String link = sender.sent.get(sender.sent.size() - 1).link();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    @Test
    @DisplayName("a link sets a new password, and the old password stops working")
    void resetSetsNewPassword() throws Exception {
        forgot(customerEmail).andExpect(status().isNoContent());

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.getFirst().link()).startsWith("http://localhost:5173/reset-password?token=");

        reset(tokenFromLastLink(), "a-brand-new-password").andExpect(status().isNoContent());

        login(customerEmail, "a-brand-new-password").andExpect(status().isOk());
        login(customerEmail, USER_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a link works once")
    void linkIsSingleUse() throws Exception {
        forgot(customerEmail).andExpect(status().isNoContent());
        String token = tokenFromLastLink();

        reset(token, "first-new-password").andExpect(status().isNoContent());
        reset(token, "second-new-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.token").value("This reset link is no longer valid. Ask for a new one."));

        login(customerEmail, "first-new-password").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a link expires, and asking again invalidates the previous one")
    void linksExpireAndSupersede() throws Exception {
        forgot(customerEmail).andExpect(status().isNoContent());
        String expiring = tokenFromLastLink();

        clock.advance(Duration.ofMinutes(31));
        reset(expiring, "too-late-password").andExpect(status().isBadRequest());

        forgot(customerEmail).andExpect(status().isNoContent());
        String older = tokenFromLastLink();
        forgot(customerEmail).andExpect(status().isNoContent());
        String newest = tokenFromLastLink();

        reset(older, "superseded-password").andExpect(status().isBadRequest());
        reset(newest, "current-password").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("an unknown address and a deactivated account look exactly like a known one, and send nothing")
    void revealsNothing() throws Exception {
        String knownBody = forgot(customerEmail).andExpect(status().isNoContent())
                .andReturn().getResponse().getContentAsString();
        sender.sent.clear();

        String unknownBody = forgot(uniqueEmail("nobody")).andExpect(status().isNoContent())
                .andReturn().getResponse().getContentAsString();
        assertThat(sender.sent).isEmpty();

        User deactivated = userRepository.findById(customerId).orElseThrow();
        deactivated.setActive(false);
        userRepository.save(deactivated);

        String inactiveBody = forgot(customerEmail).andExpect(status().isNoContent())
                .andReturn().getResponse().getContentAsString();
        assertThat(sender.sent).isEmpty();
        assertThat(knownBody).isEqualTo(unknownBody).isEqualTo(inactiveBody).isEmpty();
    }

    @Test
    @DisplayName("requests for one address are limited, so the endpoint cannot flood an inbox")
    void requestsAreLimited() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            forgot(customerEmail).andExpect(status().isNoContent());
        }
        forgot(customerEmail).andExpect(status().isTooManyRequests());
        assertThat(sender.sent).hasSize(3);

        clock.advance(Duration.ofMinutes(31));
        forgot(customerEmail).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the raw token is never stored, and used or expired links are cleaned up")
    void tokensAreStoredHashedAndCleaned() throws Exception {
        forgot(customerEmail).andExpect(status().isNoContent());
        String token = tokenFromLastLink();

        assertThat(tokenRepository.findAll())
                .allSatisfy(stored -> assertThat(stored.getTokenHash()).isNotEqualTo(token).hasSize(64));
        assertThat(tokenRepository.findByTokenHash(token)).isEmpty();

        clock.advance(Duration.ofMinutes(31));
        assertThat(passwordResetService.deleteExpiredTokens()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("the new password must meet the same rules as anywhere else")
    void validatesNewPassword() throws Exception {
        forgot(customerEmail).andExpect(status().isNoContent());

        reset(tokenFromLastLink(), "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("New password must be between 8 and 100 characters"));
        reset("not-a-real-token", "a-brand-new-password").andExpect(status().isBadRequest());
    }
}
