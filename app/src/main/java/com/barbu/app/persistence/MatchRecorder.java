package com.barbu.app.persistence;

import com.barbu.app.persistence.Entities.GameEntity;
import com.barbu.app.persistence.Entities.GamePlayerEntity;
import com.barbu.app.persistence.Entities.RoundEntity;
import com.barbu.app.persistence.Entities.RoundScoreEntity;
import com.barbu.app.persistence.Repositories.GamePlayerRepository;
import com.barbu.app.persistence.Repositories.GameRepository;
import com.barbu.app.persistence.Repositories.RoundRepository;
import com.barbu.app.persistence.Repositories.RoundScoreRepository;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.round.RoundResult;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;

@Singleton
public class MatchRecorder {

    private static final String RULESET_VERSION = "1";

    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final RoundRepository rounds;
    private final RoundScoreRepository roundScores;

    public MatchRecorder(
            GameRepository games,
            GamePlayerRepository gamePlayers,
            RoundRepository rounds,
            RoundScoreRepository roundScores) {
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.rounds = rounds;
        this.roundScores = roundScores;
    }

    public record PlayerInfo(int seat, String displayName, boolean isBot, Long userId) {}

    @Transactional
    public long record(String mode, MatchState match, List<PlayerInfo> players) {
        GameEntity game = games.save(new GameEntity(
                null,
                mode,
                match.playerCount(),
                RULESET_VERSION,
                match.seed(),
                match.variant().id(),
                Instant.now()));
        long gameId = game.id();

        List<Integer> standings = MatchEngine.standings(match);
        for (PlayerInfo p : players) {
            int rank = standings.indexOf(p.seat()) + 1;
            gamePlayers.save(new GamePlayerEntity(
                    null, gameId, p.seat(), p.userId(), p.displayName(), p.isBot(), rank, match.totals()[p.seat()]));
        }

        int contractsPerDealer = match.variant().contracts().size();
        List<RoundResult> history = match.history();
        for (int i = 0; i < history.size(); i++) {
            RoundResult result = history.get(i);
            int dealer = (i / contractsPerDealer) % match.playerCount();
            RoundEntity round = rounds.save(new RoundEntity(
                    null, gameId, i + 1, dealer, result.contract().name()));
            long roundId = round.id();
            int[] points = result.points();
            for (int seat = 0; seat < points.length; seat++) {
                roundScores.save(new RoundScoreEntity(null, roundId, seat, points[seat]));
            }
        }
        return gameId;
    }
}
