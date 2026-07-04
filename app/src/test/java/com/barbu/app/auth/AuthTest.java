package com.barbu.app.auth;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@MicronautTest
class AuthTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void register_returns_a_token_that_authenticates_me() {
        Map<?, ?> registered = client.toBlocking()
                .retrieve(
                        HttpRequest.POST("/auth/register", Map.of("username", "alice", "password", "secret123")),
                        Map.class);
        String token = (String) registered.get("token");
        assertNotNull(token);

        Map<?, ?> me = client.toBlocking().retrieve(HttpRequest.GET("/me").bearerAuth(token), Map.class);
        assertEquals("alice", me.get("username"));
    }

    @Test
    void login_works_after_register_and_wrong_password_is_rejected() {
        client.toBlocking()
                .exchange(HttpRequest.POST("/auth/register", Map.of("username", "bob", "password", "hunter2")));

        Map<?, ?> login = client.toBlocking()
                .retrieve(HttpRequest.POST("/auth/login", Map.of("username", "bob", "password", "hunter2")), Map.class);
        assertNotNull(login.get("token"));

        HttpClientResponseException bad = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking()
                        .exchange(HttpRequest.POST("/auth/login", Map.of("username", "bob", "password", "wrong"))));
        assertEquals(401, bad.getStatus().getCode());
    }

    @Test
    void register_rejects_an_overlong_username() {
        HttpClientResponseException ex = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking()
                        .exchange(HttpRequest.POST(
                                "/auth/register", Map.of("username", "x".repeat(41), "password", "pw123456"))));
        assertEquals(400, ex.getStatus().getCode());
    }

    @Test
    void me_requires_authentication() {
        HttpClientResponseException ex = assertThrows(
                HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.GET("/me")));
        assertEquals(401, ex.getStatus().getCode());
    }

    @Test
    void duplicate_username_conflicts() {
        client.toBlocking()
                .exchange(HttpRequest.POST("/auth/register", Map.of("username", "carol", "password", "pw123456")));
        HttpClientResponseException ex = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking()
                        .exchange(HttpRequest.POST(
                                "/auth/register", Map.of("username", "carol", "password", "pw123456"))));
        assertEquals(409, ex.getStatus().getCode());
    }
}
