package com.barbu.app;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class HealthTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void context_starts_and_health_is_up() {
        Map<?, ?> body = client.toBlocking().retrieve(HttpRequest.GET("/health"), Map.class);
        assertEquals("UP", body.get("status"));
    }
}
