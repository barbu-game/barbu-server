package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Demande de reprise anticipée pendant une pause : {@code {type:"resumeGame"}}.
 * Annoté {@link OpenAPIExtraSchema} pour entrer dans le contrat OpenAPI.
 */
@OpenAPIExtraSchema
public record ResumeGame() {}
