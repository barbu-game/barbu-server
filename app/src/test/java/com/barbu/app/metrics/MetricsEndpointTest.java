package com.barbu.app.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class MetricsEndpointTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void exposesPrometheusEndpointWithJvmMetrics() {
        String body = client.toBlocking().retrieve("/prometheus");
        assertTrue(body.contains("jvm_memory_used_bytes"), "JVM metrics should be exposed");
    }
}
