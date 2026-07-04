package com.barbu.app.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.openapi.annotation.OpenAPIExtraSchema;
import java.util.List;
import java.util.Map;

/**
 * Snapshot d'état rédigé pour un siège, diffusé sur le WebSocket à chaque transition.
 *
 * <p>Annoté {@link OpenAPIExtraSchema} : Micronaut OpenAPI n'inspecte que les {@code @Controller},
 * donc ce record (et ses imbriqués) n'entreraient pas dans {@code components/schemas} sans cette
 * annotation. C'est la source unique du type côté client (Orval → {@code @barbu-game/barbu-api}) :
 * tout drift de cette forme casse le typecheck web.
 *
 * <p>{@code NON_NULL} : un champ absent du snapshot ne doit pas apparaître dans le JSON (le client
 * le lit comme optionnel). Les sous-records de vote dérogent volontairement et émettent
 * {@code youVoted} même nul — le client distingue « siège n'ayant pas voté / bot » d'un vote.
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
     * Coup légal : {@code kind="card"} porte suit/rank, {@code kind="pass"} les omet (NON_NULL).
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
