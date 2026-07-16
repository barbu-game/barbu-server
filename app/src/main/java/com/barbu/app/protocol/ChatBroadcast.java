package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Outgoing chat message: broadcast identically to all seats of the room.
 *
 * <p>Annotated {@link OpenAPIExtraSchema} to enter the spec's components/schemas
 * (Micronaut OpenAPI only inspects @Controller), and thereby feed Orval →
 * {@code @barbu-game/barbu-api}. Single source of truth for the contract.
 */
@OpenAPIExtraSchema
public record ChatBroadcast(int seat, String name, String text, long ts, boolean system) {}
