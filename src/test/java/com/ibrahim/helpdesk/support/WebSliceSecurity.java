package com.ibrahim.helpdesk.support;

import com.ibrahim.helpdesk.user.entity.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Authentication for {@code @WebMvcTest} slices, which load the real
 * SecurityConfig but have no database. The request carries an already
 * verified token for the given user id and role, the same shape the real
 * token converter produces.
 */
public final class WebSliceSecurity {

    private WebSliceSecurity() {
    }

    public static RequestPostProcessor as(long userId, UserRole role) {
        return jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
