package com.barbu.rami.round;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.card.RamiDeck;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DealTest {

    @Test
    void player_left_of_dealer_gets_14_and_starts_in_play_phase() {
        RamiRound r = RamiRoundEngine.deal(5, 2, 1);
        assertEquals(3, r.current());
        assertEquals(Phase.PLAY, r.phase());
        for (int seat = 0; seat < 5; seat++) {
            assertEquals(seat == 3 ? 14 : 13, r.hand(seat).size());
        }
        assertEquals(1, r.discard().size());
        assertEquals(RamiDeck.SIZE - 66 - 1, r.stock().size());
        assertEquals(List.of(false, false, false, false, false), r.opened());
        assertTrue(r.mustPlay().isEmpty());
        assertNotNull(r.turnStart());
        assertNull(r.turnStart().turnStart());
    }

    @Test
    void deal_uses_every_card_once() {
        RamiRound r = RamiRoundEngine.deal(4, 0, 9);
        List<RamiCard> all = new ArrayList<>(r.stock());
        all.addAll(r.discard());
        r.hands().forEach(all::addAll);
        assertEquals(RamiDeck.SIZE, all.size());
        assertEquals(RamiDeck.SIZE, all.stream().map(RamiCard::id).distinct().count());
    }

    @Test
    void dealer_wraps_around_the_table() {
        assertEquals(0, RamiRoundEngine.deal(4, 3, 1).current());
    }

    @Test
    void dealer_must_sit_at_the_table() {
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.deal(4, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.deal(4, 4, 1));
    }

    @Test
    void only_four_or_five_players() {
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.deal(3, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.deal(6, 0, 1));
    }
}
