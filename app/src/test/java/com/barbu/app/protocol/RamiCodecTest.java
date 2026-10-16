package com.barbu.app.protocol;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.protocol.RamiView.RamiCardView;
import com.barbu.rami.card.RamiCard;
import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RamiMatchEngine;
import com.barbu.rami.round.RamiMove;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RamiCodecTest {

    @Test
    void parses_every_move_kind() {
        assertEquals(
                new RamiMove.Draw(RamiMove.Source.STOCK),
                RamiCodec.parseMove(Map.of("kind", "draw", "source", "STOCK")));
        assertEquals(
                new RamiMove.Draw(RamiMove.Source.DISCARD),
                RamiCodec.parseMove(Map.of("kind", "draw", "source", "DISCARD")));
        assertEquals(
                new RamiMove.Open(List.of(List.of(1, 2, 3), List.of(4, 5, 6))),
                RamiCodec.parseMove(Map.of("kind", "open", "melds", List.of(List.of(1, 2, 3), List.of(4, 5, 6)))));
        assertEquals(
                new RamiMove.NewMeld(List.of(7, 8, 9)),
                RamiCodec.parseMove(Map.of("kind", "meld", "cards", List.of(7, 8, 9))));
        assertEquals(
                new RamiMove.LayOff(3, List.of(), List.of(10)),
                RamiCodec.parseMove(Map.of("kind", "layoff", "meldId", 3, "after", List.of(10))));
        assertEquals(
                new RamiMove.SwapJoker(3, 11),
                RamiCodec.parseMove(Map.of("kind", "swapJoker", "meldId", 3, "cardId", 11)));
        assertEquals(new RamiMove.ResetTurn(), RamiCodec.parseMove(Map.of("kind", "reset")));
        assertEquals(new RamiMove.Discard(12), RamiCodec.parseMove(Map.of("kind", "discard", "cardId", 12)));
    }

    @Test
    void rejects_malformed_moves_as_illegal_arguments() {
        Map<String, Object> nullMelds = new HashMap<>();
        nullMelds.put("kind", "open");
        nullMelds.put("melds", Arrays.asList((Object) null));
        List<Map<String, Object>> bad = List.of(
                Map.of(),
                Map.of("kind", "fly"),
                Map.of("kind", "draw"),
                Map.of("kind", "draw", "source", "HAND"),
                Map.of("kind", "discard"),
                Map.of("kind", "discard", "cardId", "12"),
                Map.of("kind", "discard", "cardId", 5_000_000_000L),
                Map.of("kind", "meld", "cards", "1,2,3"),
                Map.of("kind", "meld", "cards", List.of(1, "x", 3)),
                nullMelds);
        for (Map<String, Object> move : bad) {
            assertThrows(IllegalArgumentException.class, () -> RamiCodec.parseMove(move), move.toString());
        }
        assertThrows(IllegalArgumentException.class, () -> RamiCodec.parseMove(null));
    }

    @Test
    void view_shows_only_the_seats_own_hand_and_hides_the_stock() throws Exception {
        RamiMatch m = RamiMatchEngine.startNextRound(RamiMatchEngine.newMatch(4, 3L));
        int active = m.round().current();
        int other = (active + 1) % 4;

        RamiView mine = RamiCodec.view(m, active);
        assertEquals("PLAY", mine.turnPhase());
        assertEquals(14, mine.yourHand().size());
        assertEquals(m.round().stock().size(), mine.stockCount());
        assertEquals(List.of(), mine.mustPlay());
        assertNull(mine.lastRound());

        RamiView theirs = RamiCodec.view(m, other);
        assertEquals(13, theirs.yourHand().size());
        assertEquals(List.of(), theirs.mustPlay());

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(theirs));
        assertFalse(json.has("stock"));
        assertFalse(json.toString().contains("turnStart"));
    }

    @Test
    void joker_view_has_no_suit_or_rank() throws Exception {
        RamiCardView joker = RamiCodec.card(new RamiCard(104, null));
        assertTrue(joker.joker());
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(joker));
        assertFalse(json.has("suit"));
        assertFalse(json.has("rank"));
        assertEquals(104, json.get("id").asInt());
    }

    @Test
    void between_rounds_the_view_carries_the_last_round_recap_only() {
        RamiMatch m = new RamiMatch(
                4,
                4,
                1L,
                1,
                List.of(0, 35, 18, 2),
                null,
                new com.barbu.rami.match.RoundSummary(0, true, List.of(0, 35, 18, 2)));
        RamiView v = RamiCodec.view(m, 2);
        assertNull(v.turnPhase());
        assertNull(v.yourHand());
        assertEquals(new RamiView.RoundRecap(0, true, List.of(0, 35, 18, 2)), v.lastRound());
    }

    @Test
    void only_the_active_seat_sees_its_pending_joker() {
        RamiMatch m = RamiMatchEngine.startNextRound(RamiMatchEngine.newMatch(4, 3L));
        com.barbu.rami.round.RamiRound r = m.round();
        com.barbu.rami.round.RamiRound pending = new com.barbu.rami.round.RamiRound(
                r.hands(),
                r.stock(),
                r.discard(),
                r.melds(),
                r.opened(),
                r.current(),
                r.phase(),
                java.util.Set.of(r.hand(r.current()).get(0).id()),
                r.turnStart(),
                r.nextMeldId(),
                null,
                0);
        RamiMatch withPending = new RamiMatch(4, 4, 3L, 1, m.totals(), pending, null);
        assertEquals(1, RamiCodec.view(withPending, r.current()).mustPlay().size());
        assertEquals(
                List.of(), RamiCodec.view(withPending, (r.current() + 1) % 4).mustPlay());
    }
}
