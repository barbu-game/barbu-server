package com.barbu.app.auth;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Singleton
public class JwtVerifier {

    private final byte[] secret;

    public JwtVerifier(@Value("${micronaut.security.token.jwt.signatures.secret.generator.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** The subject (username) of a valid, unexpired token, or empty. */
    public Optional<String> usernameOf(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            Date expiry = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiry != null && expiry.before(new Date())) {
                return Optional.empty();
            }
            return Optional.ofNullable(jwt.getJWTClaimsSet().getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
