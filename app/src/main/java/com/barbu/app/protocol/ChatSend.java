package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Message de tchat entrant : ce que le client envoie ({@code {type:"chat", text:"..."}}).
 *
 * <p>Annoté {@link OpenAPIExtraSchema} pour entrer dans components/schemas du spec
 * (Micronaut OpenAPI n'inspecte que les @Controller), et alimenter ainsi Orval →
 * {@code @barbu-game/barbu-api}. Source unique de vérité du contrat.
 */
@OpenAPIExtraSchema
public record ChatSend(String text) {}
