package com.barbu.app.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Instant;

/** Persisted records. Micronaut Data maps camelCase components to snake_case columns. */
public final class Entities {
    private Entities() {}

    @MappedEntity("users")
    public record UserEntity(@Id @GeneratedValue Long id, String username, String passwordHash, Instant createdAt) {}

    @MappedEntity("games")
    public record GameEntity(
            @Id @GeneratedValue Long id,
            String mode,
            int playerCount,
            String rulesetVersion,
            long seed,
            Instant createdAt) {}

    @MappedEntity("game_players")
    public record GamePlayerEntity(
            @Id @GeneratedValue Long id,
            long gameId,
            int seat,
            @Nullable Long userId,
            String displayName,
            boolean isBot,
            int finalRank,
            int finalScore) {}

    @MappedEntity("rounds")
    public record RoundEntity(
            @Id @GeneratedValue Long id, long gameId, int roundNumber, int dealerSeat, String contract) {}

    @MappedEntity("round_scores")
    public record RoundScoreEntity(@Id @GeneratedValue Long id, long roundId, int seat, int points) {}
}
