package com.barbu.app.protocol;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Commande WebSocket de reprise : le client envoie son resume token (invité) ou rien (compte,
 * identifié par le JWT). Annoté {@link OpenAPIExtraSchema} pour entrer dans le contrat, comme
 * {@code ChatSend}.
 */
@OpenAPIExtraSchema
public record ResumeCommand(@Nullable String resumeToken) {}
