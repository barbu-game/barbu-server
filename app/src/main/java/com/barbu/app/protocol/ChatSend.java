package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Incoming chat message: {@code {type:"chat", text:"..."}}.
 *
 * <p>{@link OpenAPIExtraSchema} enters it into components/schemas (Micronaut OpenAPI inspects only
 * {@code @Controller}); drives the {@code @barbu-game/barbu-api} client type.
 */
@OpenAPIExtraSchema
public record ChatSend(String text) {}
