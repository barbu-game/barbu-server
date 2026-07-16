package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Incoming chat message: what the client sends ({@code {type:"chat", text:"..."}}).
 *
 * <p>Annotated {@link OpenAPIExtraSchema} to enter the spec's components/schemas
 * (Micronaut OpenAPI only inspects @Controller), and thereby feed Orval →
 * {@code @barbu-game/barbu-api}. Single source of truth for the contract.
 */
@OpenAPIExtraSchema
public record ChatSend(String text) {}
