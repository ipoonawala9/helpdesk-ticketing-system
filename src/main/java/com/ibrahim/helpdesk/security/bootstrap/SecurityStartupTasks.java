package com.ibrahim.helpdesk.security.bootstrap;

import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Locale;

/**
 * One-off account maintenance run when the application starts. Both tasks
 * are idempotent: running them again changes nothing.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(BootstrapProperties.class)
public class SecurityStartupTasks {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupTasks.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrap;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        hashPlainTextPasswords();
        createBootstrapSuperAdmin();
    }

    /**
     * Passwords saved before hashing was introduced are stored as plain text.
     * Hash them in place so those users can still log in with the same
     * password.
     */
    @Transactional
    public int hashPlainTextPasswords() {
        List<User> users = userRepository.findWithUnhashedPasswords();
        users.forEach(user -> user.setPassword(passwordEncoder.encode(user.getPassword())));
        userRepository.saveAll(users);
        if (!users.isEmpty()) {
            log.info("Hashed {} password(s) that were stored as plain text", users.size());
        }
        return users.size();
    }

    /**
     * Creates the configured super admin, or, if that account already exists,
     * reports whether the configured password is actually in use.
     *
     * <p>The password of an existing account is only replaced when
     * {@code reset-password} is set, which is the way back in after a
     * forgotten super admin password.
     */
    @Transactional
    public void createBootstrapSuperAdmin() {
        if (!bootstrap.isConfigured()) {
            if (!userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
                log.warn("No SUPER_ADMIN exists and none is configured. Set BOOTSTRAP_SUPER_ADMIN_EMAIL and "
                        + "BOOTSTRAP_SUPER_ADMIN_PASSWORD to create one; without it no organizations can be created.");
            }
            return;
        }

        String email = bootstrap.email().strip().toLowerCase(Locale.ROOT);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            reconcileExistingSuperAdmin(existing.get());
            return;
        }

        User admin = new User();
        admin.setName(bootstrap.name() == null || bootstrap.name().isBlank() ? "Super Admin" : bootstrap.name());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(bootstrap.password()));
        admin.setRole(UserRole.SUPER_ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        log.info("Created bootstrap SUPER_ADMIN {}", email);
    }

    /**
     * The configured password is not applied to an account that already
     * exists, because that would silently reset it on every restart. Say so
     * plainly when the two differ, and honour an explicit reset.
     */
    private void reconcileExistingSuperAdmin(User existing) {
        if (bootstrap.resetPassword()) {
            existing.setPassword(passwordEncoder.encode(bootstrap.password()));
            existing.setActive(true);
            userRepository.save(existing);
            log.warn("Reset the password of {} because BOOTSTRAP_SUPER_ADMIN_RESET_PASSWORD is set. "
                    + "Unset it again so the next restart does not reset the password once more.", existing.getEmail());
            return;
        }
        if (!passwordEncoder.matches(bootstrap.password(), existing.getPassword())) {
            log.warn("{} already exists and its password is NOT the one in BOOTSTRAP_SUPER_ADMIN_PASSWORD. "
                    + "The configured password is only used when the account is first created. Sign in with the "
                    + "existing password and change it in the app, or set BOOTSTRAP_SUPER_ADMIN_RESET_PASSWORD=true "
                    + "for one restart to reset it.", existing.getEmail());
        }
    }
}
