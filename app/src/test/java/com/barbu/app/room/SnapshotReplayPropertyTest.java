package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class SnapshotReplayPropertyTest {

    private final SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());

    /**
     * decode∘encode est un inverse fidèle : re-sérialiser un snapshot désérialisé redonne le JSON
     * canonique d'origine. On déale le premier contrat pour exercer un vrai {@code TrickTakingState}
     * (mains, pli courant, plis capturés) sur toutes les tailles de table.
     */
    @Property(tries = 200)
    void snapshot_round_trip_is_json_stable(@ForAll @IntRange(min = 2, max = 10) int players, @ForAll long seed) {
        MatchState dealt = MatchEngine.startNextContract(MatchEngine.newMatch(players, seed, Variants.DEVELOPER));

        String json = codec.encode(snapshotOf(dealt, players));
        String reencoded = codec.encode(codec.decode(json));

        assertEquals(json, reencoded);
    }

    private GameSnapshot snapshotOf(MatchState match, int players) {
        String[] names = new String[players];
        boolean[] isBot = new boolean[players];
        Long[] userIds = new Long[players];
        String[] tokens = new String[players];
        for (int s = 0; s < players; s++) {
            names[s] = "P" + s;
            isBot[s] = s > 0;
            tokens[s] = "tok-" + s;
        }
        return new GameSnapshot(
                "ROOM0", players, Variants.DEVELOPER.id(), "casual", names, isBot, userIds, tokens, false, match);
    }
}
