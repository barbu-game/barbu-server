package com.barbu.app.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import java.util.List;

/**
 * The Rami part of a seat's state: only that seat's hand; the stock is a count. {@code mustPlay} and
 * {@code discardClosed} describe the active seat's turn and stay empty/false for everyone else.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RamiView(
        @Nullable String turnPhase,
        @Nullable List<RamiCardView> yourHand,
        @Nullable Integer stockCount,
        @Nullable RamiCardView discardTop,
        @Nullable List<MeldView> melds,
        @Nullable List<Boolean> opened,
        @Nullable List<Integer> mustPlay,
        @Nullable Boolean discardClosed,
        @Nullable RoundRecap lastRound) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RamiCardView(
            int id, @Nullable String suit, @Nullable String rank, boolean joker) {}

    public record MeldView(int id, int owner, String kind, List<RamiCardView> cards) {}

    public record RoundRecap(@Nullable Integer winner, boolean ramiSec, List<Integer> penalties) {}
}
