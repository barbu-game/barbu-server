package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.variant.Variants;
import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RamiMatchEngine;
import com.barbu.rami.round.RamiMove;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class RamiSnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SnapshotCodec codec = new SnapshotCodec(mapper);

    @Test
    void a_rami_table_mid_round_survives_a_snapshot_round_trip() {
        RamiMatch m = RamiMatchEngine.startNextRound(RamiMatchEngine.newMatch(4, 5L));
        int seat = m.round().current();
        m = RamiMatchEngine.apply(
                m, seat, new RamiMove.Discard(m.round().hand(seat).get(0).id()));
        GameSnapshot snap = new GameSnapshot(
                "RAMIS",
                4,
                RamiSession.VARIANT_ID,
                "private",
                new String[] {"A", "Bot 1", "Bot 2", "Bot 3"},
                new boolean[] {false, true, true, true},
                new Long[4],
                new String[] {"tok", null, null, null},
                false,
                null,
                "RAMI",
                m);

        String json = codec.encode(snap);
        GameSnapshot back = codec.decode(json);
        assertEquals("RAMI", back.gameType());
        assertEquals(json, codec.encode(back));
        assertEquals(m.round().hands(), back.rami().round().hands());
        assertEquals(
                m.round().turnStart().hands(), back.rami().round().turnStart().hands());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = GameRoom.fromSnapshot(back, mapper, scheduler, 0, null, null, null, null, 60_000, 2);
            assertEquals("RAMI", room.viewFor(0).gameType());
            assertEquals(m.round().current(), room.viewFor(0).currentActor());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void a_snapshot_without_game_type_is_read_as_barbu() throws Exception {
        GameSnapshot barbu = new GameSnapshot(
                "OLD01",
                3,
                Variants.DEVELOPER.id(),
                "private",
                new String[] {"A", "Bot 1", "Bot 2"},
                new boolean[] {false, true, true},
                new Long[3],
                new String[] {"tok", null, null},
                false,
                MatchEngine.newMatch(3, 1L, Variants.DEVELOPER));
        ObjectNode legacy = (ObjectNode) mapper.readTree(codec.encode(barbu));
        legacy.remove("gameType");
        legacy.remove("rami");

        GameSnapshot back = codec.decode(mapper.writeValueAsString(legacy));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameRoom room = GameRoom.fromSnapshot(back, mapper, scheduler, 0, null, null, null, null, 60_000, 2);
            assertEquals("BARBU", room.viewFor(0).gameType());
            assertEquals("developer", room.viewFor(0).variant().id());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
