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

    @Test
    void defaults_to_english_when_no_language_requested() {
        Map<String, Object> classic = fetchClassic(HttpRequest.GET("/variants"));
        assertEquals(
                "The traditional seven contracts: tricks, hearts, queens, the King of Hearts (le Barbu),"
                        + " the last two tricks, the montante, and the salad.",
                classic.get("description"));
        assertEquals("No hearts", titleOf(classic, "NO_HEARTS"));
        assertEquals("-2 per heart", ruleOf(classic, "NO_HEARTS"));
        assertEquals(
                "Domino: empty your hand first; zero-sum ranking by finishing order.", ruleOf(classic, "MONTANTE"));
    }

    @Test
    void serves_french_rules_when_accept_language_is_french() {
        Map<String, Object> classic = fetchClassic(HttpRequest.GET("/variants").header("Accept-Language", "fr-FR"));
        assertEquals(
                "Les sept contrats traditionnels : les plis, les cœurs, les dames, le Roi de cœur (le Barbu),"
                        + " les deux derniers plis, la montante et la salade.",
                classic.get("description"));
        assertEquals("Sans cœurs", titleOf(classic, "NO_HEARTS"));
        assertEquals("Roi de cœur", titleOf(classic, "NO_KING_OF_HEARTS"));
        assertEquals("-2 par cœur", ruleOf(classic, "NO_HEARTS"));
        assertEquals("-20 par Roi de cœur", ruleOf(classic, "NO_KING_OF_HEARTS"));
        assertEquals(
                "Domino : videz votre main en premier ; classement à somme nulle selon l'ordre d'arrivée.",
                ruleOf(classic, "MONTANTE"));
    }

    @Test
    void french_normalized_rules_are_fully_translated() {
        Map<String, Object> developer = fetch(HttpRequest.GET("/variants").header("Accept-Language", "fr")).stream()
                .filter(v -> "developer".equals(v.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("60 répartis sur les plis", ruleOf(developer, "NO_TRICKS"));
        assertEquals("60 répartis sur les rois rouges", ruleOf(developer, "NO_RED_KINGS"));
    }

    @Test
    void no_french_rule_text_leaks_english_words() {
        for (Map<String, Object> variant : fetch(HttpRequest.GET("/variants").header("Accept-Language", "fr"))) {
            for (Map<String, Object> c : contractsOf(variant)) {
                String rule = (String) c.get("rule");
                assertFalse(rule.contains(" per "), "leaked English in " + rule);
                assertFalse(rule.contains(" spread over "), "leaked English in " + rule);
            }
        }
    }

    private Map<String, Object> fetchClassic(HttpRequest<?> request) {
        return fetch(request).stream()
                .filter(v -> "classic".equals(v.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetch(HttpRequest<?> request) {
        return client.toBlocking()
                .retrieve(request, Argument.listOf((Class<Map<String, Object>>) (Class<?>) Map.class));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> contractsOf(Map<String, Object> variant) {
        return (List<Map<String, Object>>) variant.get("contracts");
    }

    private static Map<String, Object> contract(Map<String, Object> variant, String key) {
        return contractsOf(variant).stream()
                .filter(c -> key.equals(c.get("key")))
                .findFirst()
                .orElseThrow();
    }

    private static String titleOf(Map<String, Object> variant, String key) {
        return (String) contract(variant, key).get("title");
    }

    private static String ruleOf(Map<String, Object> variant, String key) {
        return (String) contract(variant, key).get("rule");
    }
}
