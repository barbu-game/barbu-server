package com.barbu.app.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.persistence.MatchRecorder.PlayerInfo;
import com.barbu.app.persistence.Repositories.GamePlayerRepository;
import com.barbu.app.persistence.Repositories.GameRepository;
import com.barbu.app.persistence.Repositories.RoundRepository;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
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

    @Test
    void barbu_games_are_tagged_barbu() {
        long gameId = recorder.record(
                "private",
                playFull(3, 7L),
                List.of(
                        new PlayerInfo(0, "Alice", false, null),
                        new PlayerInfo(1, "Bot 1", true, null),
                        new PlayerInfo(2, "Bot 2", true, null)));
        assertEquals("BARBU", games.findById(gameId).orElseThrow().gameType());
    }

    @Test
    void records_a_rami_game_and_its_ranking() {
        com.barbu.rami.match.RamiMatch rami =
                com.barbu.bot.rami.RamiSimulator.play(4, 3L, new com.barbu.bot.rami.RamiBot());
        long gameId = recorder.recordRami(
                "private",
                rami,
                List.of(
                        new PlayerInfo(0, "Alice", false, null),
                        new PlayerInfo(1, "Bot 1", true, null),
                        new PlayerInfo(2, "Bot 2", true, null),
                        new PlayerInfo(3, "Bot 3", true, null)));

        var game = games.findById(gameId).orElseThrow();
        assertEquals("RAMI", game.gameType());
        assertEquals("rami51", game.variant());
        assertEquals(4, count(gamePlayers.findByGameId(gameId)));
        var recorded = StreamSupport.stream(rounds.findByGameId(gameId).spliterator(), false)
                .toList();
        assertEquals(4, recorded.size(), "one row per round");
        assertTrue(recorded.stream().allMatch(r -> r.contract().equals("RAMI")));
        assertEquals(
                List.of(0, 1, 2, 3),
                recorded.stream().map(r -> r.dealerSeat()).sorted().toList());
        int winner = com.barbu.rami.match.RamiMatchEngine.standings(rami).get(0);
        var first = StreamSupport.stream(gamePlayers.findByGameId(gameId).spliterator(), false)
                .filter(p -> p.finalRank() == 1)
                .findFirst()
                .orElseThrow();
        assertEquals(winner, first.seat());
        assertEquals((int) rami.totals().get(winner), first.finalScore());
    }

    private static MatchState playFull(int n, long seed) {
        MatchState m = MatchEngine.newMatch(n, seed);
        while (!MatchEngine.isComplete(m)) {
            m = MatchEngine.playOut(MatchEngine.startNextContract(m));
        }
        return m;
    }

    private static long count(Iterable<?> it) {
        return StreamSupport.stream(it.spliterator(), false).count();
    }
}
