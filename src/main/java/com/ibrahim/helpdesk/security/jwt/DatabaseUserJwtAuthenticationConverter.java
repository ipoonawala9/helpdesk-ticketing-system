package com.ibrahim.helpdesk.security.jwt;

import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Turns a verified token into an authenticated request by loading its user.
 *
 * <p>A token whose user no longer exists or has been deactivated is rejected,
 * and authorities come from the user's current role in the database rather
 * than from the token. Deactivating a user or changing their role therefore
 * applies to their very next request.
 */
@RequiredArgsConstructor
public class DatabaseUserJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseUserId(jwt)
                .flatMap(userRepository::findById)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .orElseThrow(() -> new InvalidBearerTokenException("Access token is no longer valid"));

        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }

    private static java.util.Optional<Long> parseUserId(Jwt jwt) {
        try {
            return java.util.Optional.of(Long.parseLong(jwt.getSubject()));
        } catch (NumberFormatException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
