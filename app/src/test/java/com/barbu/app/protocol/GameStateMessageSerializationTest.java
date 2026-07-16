package com.barbu.app.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.app.protocol.GameStateMessage.MoveView;
import com.barbu.app.protocol.GameStateMessage.PlayerInfo;
import com.barbu.app.protocol.GameStateMessage.TrickView;
import com.barbu.app.protocol.GameStateMessage.VoteState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Locks the JSON shape of the state snapshot: the client reads an absent field as optional, but
 * distinguishes {@code youVoted:null} (did not vote / bot) from a vote. These invariants are not
 * deducible from the type, hence these assertions.
 */
class GameStateMessageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode serialize(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    @Test
    void optional_fields_absent_from_a_lobby_snapshot() throws Exception {
        GameStateMessage lobby = new GameStateMessage(
                "state",
                "ROOM",
                3,
                0,
                null,
                "LOBBY",
                List.of(new PlayerInfo(0, "Empty", false, false)),
                new GameStateMessage.VariantInfo("developer", "Developer"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        JsonNode json = serialize(lobby);
        assertEquals("state", json.get("type").asText());
        assertEquals("LOBBY", json.get("phase").asText());
        assertFalse(json.has("resumeToken"), "absent token must not be serialized");
        assertFalse(json.has("dealer"), "no match yet: dealer must be absent, not null");
        assertFalse(json.has("trick"));
    }

    @Test
    void a_bot_seat_emits_youVoted_as_null_rather_than_omitting_it() throws Exception {
        JsonNode json = serialize(new VoteState(true, 2, 1, null));
        assertTrue(json.has("youVoted"), "the client distinguishes a null vote from an absent field");
        assertTrue(json.get("youVoted").isNull());
    }

    @Test
    void a_pass_move_omits_suit_and_rank() throws Exception {
        JsonNode pass = serialize(MoveView.pass());
        assertEquals("pass", pass.get("kind").asText());
        assertFalse(pass.has("suit"));
        assertFalse(pass.has("rank"));

        JsonNode card = serialize(MoveView.card("HEARTS", "ACE"));
        assertEquals("card", card.get("kind").asText());
        assertEquals("HEARTS", card.get("suit").asText());
        assertEquals("ACE", card.get("rank").asText());
    }

    @Test
    void an_incomplete_trick_omits_complete_and_taker() throws Exception {
        JsonNode open = serialize(new TrickView(0, List.of(), null, null));
        assertFalse(open.has("complete"));
        assertFalse(open.has("taker"));

        JsonNode done = serialize(new TrickView(0, List.of(), true, 2));
        assertTrue(done.get("complete").asBoolean());
        assertEquals(2, done.get("taker").asInt());
    }
}
