package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Incoming pause vote: {@code {type:"castPauseVote", pause:true|false}}. Annotated
 * {@link OpenAPIExtraSchema} to enter components/schemas and feed Orval.
 */
@OpenAPIExtraSchema
public record CastPauseVote(boolean pause) {}
