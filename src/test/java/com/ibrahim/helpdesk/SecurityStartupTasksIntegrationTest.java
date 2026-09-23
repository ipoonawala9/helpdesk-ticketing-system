package com.ibrahim.helpdesk;

import com.ibrahim.helpdesk.organization.entity.Organization;
import com.ibrahim.helpdesk.organization.repository.OrganizationRepository;
import com.ibrahim.helpdesk.security.bootstrap.BootstrapProperties;
import com.ibrahim.helpdesk.security.bootstrap.SecurityStartupTasks;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Startup account maintenance: hashing passwords stored before hashing
 * existed, and the bootstrap super admin.
 */
class SecurityStartupTasksIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private SecurityStartupTasks startupTasks;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("a password stored as plain text is hashed on startup and the user logs in with it unchanged")
    void plainTextPasswordsAreMigrated() throws Exception {
        Organization acme = organizationRepository.findById(createOrganization("Acme Ltd")).orElseThrow();

        // A row as it looked before passwords were hashed.
        User legacy = new User();
        legacy.setName("Legacy Customer");
        legacy.setEmail(uniqueEmail("Legacy Customer"));
        legacy.setPassword("legacy-plain-password");
        legacy.setRole(UserRole.CUSTOMER);
        legacy.setActive(true);
        legacy.setOrganization(acme);
        legacy = userRepository.save(legacy);

        assertThat(startupTasks.hashPlainTextPasswords()).isGreaterThanOrEqualTo(1);

        String stored = userRepository.findById(legacy.getId()).orElseThrow().getPassword();
        assertThat(stored).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("legacy-plain-password", stored)).isTrue();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"legacy-plain-password"}
                                """.formatted(legacy.getEmail())))
                .andExpect(status().isOk());

        assertThat(startupTasks.hashPlainTextPasswords()).as("running again changes nothing").isZero();
        assertThat(userRepository.findById(legacy.getId()).orElseThrow().getPassword()).isEqualTo(stored);
    }

    @Test
    @DisplayName("the bootstrap super admin is created once and never overwritten")
    void bootstrapIsIdempotent() {
        User admin = userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(admin.getPassword()).startsWith("{bcrypt}");

        String changedHash = passwordEncoder.encode("changed-by-the-admin-later");
        admin.setPassword(changedHash);
        userRepository.save(admin);

        startupTasks.createBootstrapSuperAdmin();

        assertThat(userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow().getPassword())
                .isEqualTo(changedHash);
        assertThat(userRepository.findAll().stream().filter(u -> SUPER_ADMIN_EMAIL.equals(u.getEmail())))
                .hasSize(1);

        // Restore the configured password for other tests sharing this database.
        admin.setPassword(passwordEncoder.encode(SUPER_ADMIN_PASSWORD));
        userRepository.save(admin);
    }

    @Test
    @DisplayName("an existing super admin keeps its password when the configured one changes")
    void existingSuperAdminKeepsItsPassword() {
        User admin = userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow();
        String before = admin.getPassword();

        // The configured password in application.properties differs from a password set later.
        admin.setPassword(passwordEncoder.encode("changed-in-the-app"));
        userRepository.save(admin);

        startupTasks.createBootstrapSuperAdmin();

        String after = userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("changed-in-the-app", after)).isTrue();
        assertThat(passwordEncoder.matches(SUPER_ADMIN_PASSWORD, after)).isFalse();

        // Put the fixture back for the other tests in this context.
        admin.setPassword(before);
        userRepository.save(admin);
    }

    @Test
    @DisplayName("an explicit reset restores the configured password and reactivates the account")
    void explicitResetRestoresConfiguredPassword() {
        User admin = userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow();
        String before = admin.getPassword();
        admin.setPassword(passwordEncoder.encode("forgotten"));
        admin.setActive(false);
        userRepository.save(admin);

        // What BOOTSTRAP_SUPER_ADMIN_RESET_PASSWORD=true does on the next start.
        new SecurityStartupTasks(userRepository, passwordEncoder,
                new BootstrapProperties(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD, "Super Admin", true))
                .createBootstrapSuperAdmin();

        User reset = userRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(SUPER_ADMIN_PASSWORD, reset.getPassword())).isTrue();
        assertThat(reset.getActive()).isTrue();

        reset.setPassword(before);
        userRepository.save(reset);
    }
}
