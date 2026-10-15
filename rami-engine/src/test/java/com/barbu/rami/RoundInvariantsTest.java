package com.barbu.rami;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.card.RamiDeck;
import com.barbu.rami.round.Phase;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import com.barbu.rami.round.RamiRoundEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class RoundInvariantsTest {

    static List<Integer> allIds(RamiRound r) {
        List<Integer> ids = new ArrayList<>();
        r.hands().forEach(h -> h.forEach(c -> ids.add(c.id())));
        r.stock().forEach(c -> ids.add(c.id()));
        r.discard().forEach(c -> ids.add(c.id()));
        r.melds().forEach(m -> m.cards().forEach(c -> ids.add(c.id())));
        return ids;
    }

    // 300 draw/discard turns exhaust the stock until the recycle limit ends the round.
    @Property(tries = 100)
    void cards_are_conserved_through_draws_discards_and_recycling(
            @ForAll @IntRange(min = 4, max = 5) int n, @ForAll @LongRange(min = 0, max = 10_000) long seed) {
        RamiRound r = RamiRoundEngine.deal(n, 0, seed);
        Random rnd = new Random(seed);
        for (int turn = 0; turn < 300 && r.phase() != Phase.FINISHED; turn++) {
            int seat = r.current();
            if (r.phase() == Phase.DRAW) {
                r = RamiRoundEngine.apply(r, seat, new RamiMove.Draw(RamiMove.Source.STOCK));
            }
            if (r.phase() == Phase.FINISHED) {
                break;
            }
            List<RamiCard> hand = r.hand(seat);
            r = RamiRoundEngine.apply(
                    r,
                    seat,
                    new RamiMove.Discard(hand.get(rnd.nextInt(hand.size())).id()));
            List<Integer> ids = allIds(r);
            assertEquals(RamiDeck.SIZE, ids.size());
            assertEquals(RamiDeck.SIZE, ids.stream().distinct().count());
        }
    }
}
