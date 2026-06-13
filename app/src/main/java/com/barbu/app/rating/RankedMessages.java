package com.barbu.app.rating;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;
import java.util.List;

/**
 * DTO du message WebSocket de fin de partie ranked (delta ELO par siège). Annotés
 * {@link OpenAPIExtraSchema} pour entrer dans le spec sans controller factice, comme le tchat.
 */
public final class RankedMessages {

    private RankedMessages() {}

    @OpenAPIExtraSchema
    public record RankedResultEntry(int seat, int ratingBefore, int ratingAfter, int delta) {}

    @OpenAPIExtraSchema
    public record RankedResult(List<RankedResultEntry> entries) {}
}
