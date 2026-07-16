package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Request for an early resume during a pause: {@code {type:"resumeGame"}}.
 * Annotated {@link OpenAPIExtraSchema} to enter the OpenAPI contract.
 */
@OpenAPIExtraSchema
public record ResumeGame() {}
