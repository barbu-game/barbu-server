package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Pins what every seat sees during whole Barbu games, so a refactor of the room cannot drift. */
class GameRoomGoldenTest {

    // Pinned on the pre-GameSession room; a change here is a visible behaviour change.
    private static final String DEVELOPER_4P = "aa593dd8d66c3ef5cc04d5e5a27c0ebaa78293daeb7aad2118d272f7038f4a83";
    private static final String CLASSIC_5P = "6639c5e1a670b16d8173cf13e7cbeb89f25a1392c697f0b3b5021b265887489c";

    @Test
    void developer_variant_four_bots() throws Exception {
        assertEquals(DEVELOPER_4P, playthroughDigest("developer", 4, 42L));
    }

    @Test
    void classic_variant_five_bots() throws Exception {
        assertEquals(CLASSIC_5P, playthroughDigest("classic", 5, 7L));
    }

    static String playthroughDigest(String variantId, int players, long seed) throws Exception {
        InlineScheduler scheduler = new InlineScheduler();
        ObjectMapper mapper = new ObjectMapper();
        GameRoom room = new GameRoom("GOLD", players, Variants.byId(variantId), mapper, scheduler, 0, null, null);
        for (int i = 0; i < players; i++) {
            room.addBot();
        }
        room.start(seed);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        String previous = "";
        int steps = 0;
        do {
            ArrayNode views = mapper.createArrayNode();
            for (int seat = 0; seat < players; seat++) {
                ObjectNode view = mapper.valueToTree(room.viewFor(seat));
                view.remove("gameType");
                views.add(view);
            }
            String json = mapper.writeValueAsString(views);
            if (!json.equals(previous)) {
                sha.update(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                previous = json;
            }
            steps++;
        } while (scheduler.step() && steps < 200_000);
        assertEquals("GAME_OVER", room.viewFor(0).phase());
        return HexFormat.of().formatHex(sha.digest());
    }
}
