package com.barbu.app.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.persistence.MatchRecorder.PlayerInfo;
import com.barbu.app.persistence.Repositories.GamePlayerRepository;
import com.barbu.app.persistence.Repositories.GameRepository;
import com.barbu.app.persistence.Repositories.RoundRepository;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.Contract;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

@MicronautTest
class MatchRecorderTest {

    @Inject
    MatchRecorder recorder;

    @Inject
    GameRepository games;

    @Inject
    GamePlayerRepository gamePlayers;

    @Inject
    RoundRepository rounds;

    @Test
    void records_game_players_rounds_and_scores() {
        MatchState match = playFull(3, 7L);
        long gameId = recorder.record(
                "private",
                match,
                List.of(
                        new PlayerInfo(0, "Alice", false, null),
                        new PlayerInfo(1, "Bot 1", true, null),
                        new PlayerInfo(2, "Bot 2", true, null)));

        assertTrue(games.findById(gameId).isPresent());
        assertEquals(3, count(gamePlayers.findByGameId(gameId)));
        assertEquals(15, count(rounds.findByGameId(gameId)), "5 contracts x 3 dealers");

        var winner = StreamSupport.stream(gamePlayers.findByGameId(gameId).spliterator(), false)
                .filter(p -> p.finalRank() == 1)
                .findFirst()
                .orElseThrow();
        assertEquals(3, count(gamePlayers.findByGameId(gameId)));
        assertNotNull(winner.displayName());
    }

    private static MatchState playFull(int n, long seed) {
        MatchState m = MatchEngine.newMatch(n, seed);
        while (!MatchEngine.isComplete(m)) {
            m = MatchEngine.playOut(MatchEngine.chooseContract(m, nextUnplayed(m)));
        }
        return m;
    }

    private static Contract nextUnplayed(MatchState m) {
        for (Contract contract : Contract.values()) {
            if (!m.playedByDealer().contains(contract)) {
                return contract;
            }
        }
        throw new IllegalStateException();
    }

    private static long count(Iterable<?> it) {
        return StreamSupport.stream(it.spliterator(), false).count();
    }
}
