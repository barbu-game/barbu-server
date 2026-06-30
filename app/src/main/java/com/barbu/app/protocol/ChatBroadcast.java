package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Message de tchat sortant : diffusé à l'identique à tous les sièges de la room.
 *
 * <p>Annoté {@link OpenAPIExtraSchema} pour entrer dans components/schemas du spec
 * (Micronaut OpenAPI n'inspecte que les @Controller), et alimenter ainsi Orval →
 * {@code @barbu-game/barbu-api}. Source unique de vérité du contrat.
 */
@OpenAPIExtraSchema
public record ChatBroadcast(int seat, String name, String text, long ts, boolean system) {}
