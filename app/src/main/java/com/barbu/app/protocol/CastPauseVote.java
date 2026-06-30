package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Vote de pause entrant : {@code {type:"castPauseVote", pause:true|false}}. Annoté
 * {@link OpenAPIExtraSchema} pour entrer dans components/schemas et alimenter Orval.
 */
@OpenAPIExtraSchema
public record CastPauseVote(boolean pause) {}
