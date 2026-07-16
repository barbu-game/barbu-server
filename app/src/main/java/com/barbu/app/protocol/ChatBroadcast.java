package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Outgoing chat message, broadcast identically to all seats.
 *
 * <p>{@link OpenAPIExtraSchema} enters it into components/schemas (Micronaut OpenAPI inspects only
 * {@code @Controller}); drives the {@code @barbu-game/barbu-api} client type.
 */
@OpenAPIExtraSchema
public record ChatBroadcast(int seat, String name, String text, long ts, boolean system) {}
