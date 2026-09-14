package com.ibrahim.helpdesk.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Settings for issuing and verifying access tokens, bound from
 * {@code helpdesk.security.jwt.*}.
 *
 * @param secret     HMAC-SHA256 signing key; at least 32 bytes. Supplied through
 *                   the environment and never committed.
 * @param accessTokenTtl how long an access token stays valid
 * @param issuer     value of the token's {@code iss} claim, which is verified
 */
@ConfigurationProperties(prefix = "helpdesk.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT1H") Duration accessTokenTtl,
        @DefaultValue("helpdesk") String issuer
) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "helpdesk.security.jwt.secret is not set. Provide a random value of at least "
                            + MIN_SECRET_BYTES + " characters through the JWT_SECRET environment variable.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "helpdesk.security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalStateException("helpdesk.security.jwt.access-token-ttl must be positive");
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
