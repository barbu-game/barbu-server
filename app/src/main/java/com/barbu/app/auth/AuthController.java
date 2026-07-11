package com.barbu.app.auth;

import com.barbu.app.PlayerNames;
import com.barbu.app.persistence.Entities.UserEntity;
import com.barbu.app.persistence.Repositories.UserRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.openapi.annotation.OpenAPIExtraSchema;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Controller("/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
public class AuthController {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final JwtIssuer jwt;

    public AuthController(UserRepository users, PasswordHasher hasher, JwtIssuer jwt) {
        this.users = users;
        this.hasher = hasher;
        this.jwt = jwt;
    }

    public record Credentials(
            @NotBlank String username, @NotBlank String password) {}

    @OpenAPIExtraSchema
    public record AuthToken(String token, String username) {}

    @OpenAPIExtraSchema
    public record ApiError(String error) {}

    @Post("/register")
    public HttpResponse<?> register(@Body Credentials creds) {
        String username = creds.username() == null ? "" : creds.username().trim();
        if (!PlayerNames.isValidAccountName(username)) {
            return HttpResponse.badRequest(
                    new ApiError("username must be 1 to " + PlayerNames.MAX_LEN + " characters"));
        }
        if (users.existsByUsername(username)) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.CONFLICT)
                    .body(new ApiError("username already taken"));
        }
        users.save(new UserEntity(null, username, hasher.hash(creds.password()), Instant.now()));
        return HttpResponse.ok(new AuthToken(jwt.issue(username), username));
    }

    @Post("/login")
    public HttpResponse<?> login(@Body Credentials creds) {
        String username = creds.username() == null ? "" : creds.username().trim();
        return users.findByUsername(username)
                .filter(u -> hasher.matches(creds.password(), u.passwordHash()))
                .<HttpResponse<?>>map(u -> HttpResponse.ok(new AuthToken(jwt.issue(u.username()), u.username())))
                .orElseGet(() -> HttpResponse.unauthorized().body(new ApiError("invalid credentials")));
    }
}
