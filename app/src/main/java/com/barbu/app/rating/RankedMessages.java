package com.barbu.app.rating;

import io.micronaut.openapi.annotation.OpenAPIExtraSchema;
import java.util.List;

/**
 * DTO for the ranked end-of-game WebSocket message (ELO delta per seat). Annotated
 * {@link OpenAPIExtraSchema} to enter the spec without a dummy controller.
 */
public final class RankedMessages {

    private RankedMessages() {}

    @OpenAPIExtraSchema
    public record RankedResultEntry(int seat, int ratingBefore, int ratingAfter, int delta) {}

    @OpenAPIExtraSchema
    public record RankedResult(List<RankedResultEntry> entries) {}
}
