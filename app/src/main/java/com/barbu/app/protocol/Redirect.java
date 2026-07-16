package com.barbu.app.protocol;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;

/**
 * Tells the client to reconnect to the pod owning the table (routing by roomId):
 * {@code {type:"redirect", roomId, pod}}. Annotated {@link OpenAPIExtraSchema} to enter the contract.
 */
@OpenAPIExtraSchema
public record Redirect(String type, String roomId, String pod) {
    public static Redirect to(String roomId, String pod) {
        return new Redirect("redirect", roomId, pod);
    }
}
