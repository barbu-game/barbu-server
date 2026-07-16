package com.barbu.app.protocol;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * WebSocket resume command: the client sends its resume token (guest) or nothing (account,
 * identified by the JWT). Annotated {@link OpenAPIExtraSchema} to enter the contract, like
 * {@code ChatSend}.
 */
@OpenAPIExtraSchema
public record ResumeCommand(@Nullable String resumeToken) {}
