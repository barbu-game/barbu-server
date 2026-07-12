package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Indique au client de se reconnecter au pod propriétaire de la table (routage par roomId) :
 * {@code {type:"redirect", roomId, pod}}. Annoté {@link OpenAPIExtraSchema} pour entrer dans le contrat.
 */
@OpenAPIExtraSchema
public record Redirect(String type, String roomId, String pod) {
    public static Redirect to(String roomId, String pod) {
        return new Redirect("redirect", roomId, pod);
    }
}
