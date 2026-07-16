package com.barbu.app.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.openapi.annotation.OpenAPIExtraSchema;
import java.util.List;
import java.util.Map;

/**
 * State snapshot redacted for a seat, broadcast over the WebSocket on every transition.
 *
 * <p>{@link OpenAPIExtraSchema} enters it (and its nested records) into components/schemas
 * (Micronaut OpenAPI inspects only {@code @Controller}); drives the {@code @barbu-game/barbu-api} client type.
 *
 * <p>{@code NON_NULL}: fields absent from the snapshot stay out of the JSON. The vote sub-records
 * deliberately opt out, emitting {@code youVoted} even when null, to distinguish "not voted / bot" from a vote.
 */
@OpenAPIExtraSchema
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameStateMessage(
        String type,
        String roomId,
        int playerCount,
        int yourSeat,
        @Nullable String resumeToken,
        String phase,
        List<PlayerInfo> players,
        VariantInfo variant,
        @Nullable Integer dealer,
        @Nullable Integer roundNumber,
        @Nullable Integer plannedRounds,
        @Nullable List<Integer> totals,
        @Nullable List<Standing> standings,
        @Nullable VoteState stopVote,
        @Nullable PauseVoteState pauseVote,
        @Nullable PausedState paused,
        @Nullable Integer currentActor,
        @Nullable Long turnDeadlineEpochMs,
        @Nullable String contract,
        @Nullable List<Integer> handCounts,
        @Nullable List<CardView> yourHand,
        @Nullable List<Integer> roundScores,
        @Nullable List<List<CardView>> captured,
        @Nullable TrickView trick,
        @Nullable Map<String, BoardCell> board,
        @Nullable Boolean resolving,
        @Nullable String nextContract,
        @Nullable LastRound lastRound,
        @Nullable List<MoveView> yourLegalMoves) {

    public record PlayerInfo(int seat, String name, boolean bot, boolean connected) {}

    public record VariantInfo(String id, String name) {}

    public record Standing(int rank, int seat, String name, int total) {}

    public record LastRound(String contract, List<RankRow> ranking) {
        public record RankRow(int rank, int seat, String name, int points) {}
    }

    public record VoteState(
            boolean open,
            int humans,
            int stopVotes,
            @Nullable Boolean youVoted) {}

    public record PauseVoteState(
            boolean open,
            int humans,
            int pauseVotes,
            @Nullable Boolean youVoted) {}

    public record PausedState(boolean active, long endsAtMs) {}

    public record CardView(String suit, String rank) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrickView(
            int leader,
            List<TrickPlay> plays,
            @Nullable Boolean complete,
            @Nullable Integer taker) {
        public record TrickPlay(int seat, CardView card) {}
    }

    public record BoardCell(boolean opened, int low, int high) {}

    /**
     * Legal move: {@code kind="card"} carries suit/rank, {@code kind="pass"} omits them (NON_NULL).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MoveView(
            String kind, @Nullable String suit, @Nullable String rank) {
        public static MoveView card(String suit, String rank) {
            return new MoveView("card", suit, rank);
        }

        public static MoveView pass() {
            return new MoveView("pass", null, null);
        }
    }
}
