package com.barbu.app.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Issues HS256 tokens signed with the same secret Micronaut Security uses to validate
 * incoming bearer tokens, so issuance and validation stay in lockstep.
 */
@Singleton
public class JwtIssuer {

    private static final long TTL_HOURS = 24;

    private final byte[] secret;

    public JwtIssuer(@Value("${micronaut.security.token.jwt.signatures.secret.generator.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String username) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign token", e);
        }
    }
}
