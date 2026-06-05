package com.barbu.app.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.inject.Singleton;

@Singleton
public class PasswordHasher {

    public String hash(String raw) {
        return BCrypt.withDefaults().hashToString(12, raw.toCharArray());
    }

    public boolean matches(String raw, String hash) {
        return BCrypt.verifyer().verify(raw.toCharArray(), hash).verified;
    }
}
