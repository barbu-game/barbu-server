package com.barbu.app.variant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@MicronautTest
class VariantControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    @SuppressWarnings("unchecked")
    void lists_all_variants_with_contracts_and_generated_rules() {
        List<Map<String, Object>> variants = client.toBlocking()
                .retrieve(HttpRequest.GET("/variants"), Argument.listOf((Class<Map<String, Object>>)
                        (Class<?>) Map.class));
        assertEquals(4, variants.size());
        Map<String, Object> classic = variants.stream()
                .filter(v -> "classic".equals(v.get("id")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> contracts = (List<Map<String, Object>>) classic.get("contracts");
        assertEquals(7, contracts.size());
        for (Map<String, Object> c : contracts) {
            assertTrue(c.containsKey("key"));
            assertTrue(c.containsKey("title"));
            assertFalse(((String) c.get("rule")).isBlank());
        }
    }
}
