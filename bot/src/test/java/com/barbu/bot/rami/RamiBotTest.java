package com.barbu.bot.rami;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.rami.card.RamiCard;
import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.round.Phase;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import com.barbu.rami.round.RamiRoundEngine;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class RamiBotTest {
    private int nextId = 0;

    private RamiCard card(Suit suit, Rank rank) {
        return new RamiCard(nextId++, new Card(suit, rank));
    }

    private List<RamiCard> filler() {
        return List.of(card(Suit.CLUBS, Rank.TWO), card(Suit.DIAMONDS, Rank.FOUR), card(Suit.SPADES, Rank.SIX));
    }

    @Test
    void opens_when_its_melds_reach_51() {
        List<RamiCard> hand = List.of(
                card(Suit.HEARTS, Rank.JACK),
                card(Suit.HEARTS, Rank.QUEEN),
                card(Suit.HEARTS, Rank.KING),
                card(Suit.SPADES, Rank.SEVEN),
                card(Suit.DIAMONDS, Rank.SEVEN),
                card(Suit.CLUBS, Rank.SEVEN),
                card(Suit.CLUBS, Rank.THREE));
        RamiRound r = RamiRoundEngine.setUp(
                0,
                Phase.PLAY,
                List.of(hand, filler(), filler(), filler()),
                List.of(card(Suit.HEARTS, Rank.TWO)),
                List.of(card(Suit.SPADES, Rank.KING)),
                List.of(),
                List.of(false, false, false, false));
        RamiMove move = new RamiBot().chooseMove(r, 0);
        assertInstanceOf(RamiMove.Open.class, move);
        assertTrue(RamiRoundEngine.apply(r, 0, move).opened().get(0));
    }

    @Test
    void discards_an_isolated_card_before_one_with_neighbours() {
        RamiCard king = card(Suit.SPADES, Rank.KING);
        List<RamiCard> hand = List.of(card(Suit.HEARTS, Rank.TWO), card(Suit.HEARTS, Rank.THREE), king);
        RamiRound r = RamiRoundEngine.setUp(
                0,
                Phase.PLAY,
                List.of(hand, filler(), filler(), filler()),
                List.of(card(Suit.HEARTS, Rank.NINE)),
                List.of(card(Suit.CLUBS, Rank.KING)),
                List.of(),
                List.of(false, false, false, false));
        assertEquals(new RamiMove.Discard(king.id()), new RamiBot().chooseMove(r, 0));
    }

    @Test
    void keeps_high_cards_before_opening_and_sheds_them_after() {
        RamiCard nine = card(Suit.DIAMONDS, Rank.NINE);
        RamiCard king = card(Suit.SPADES, Rank.KING);
        List<RamiCard> hand = List.of(card(Suit.HEARTS, Rank.TWO), card(Suit.HEARTS, Rank.THREE), nine, king);
        for (boolean opened : List.of(false, true)) {
            RamiRound r = RamiRoundEngine.setUp(
                    0,
                    Phase.PLAY,
                    List.of(hand, filler(), filler(), filler()),
                    List.of(card(Suit.HEARTS, Rank.NINE)),
                    List.of(card(Suit.CLUBS, Rank.KING)),
                    List.of(),
                    List.of(opened, false, false, false));
            RamiCard expected = opened ? king : nine;
            assertEquals(new RamiMove.Discard(expected.id()), new RamiBot().chooseMove(r, 0));
        }
    }

    @Property(tries = 30)
    void bot_matches_always_complete_and_conserve_cards(
            @ForAll @IntRange(min = 4, max = 5) int n, @ForAll @LongRange(min = 0, max = 100_000) long seed) {
        RamiMatch m = RamiSimulator.play(n, seed, new RamiBot());
        assertTrue(m.isComplete());
        assertEquals(n, m.roundNumber());
        assertTrue(m.totals().stream().allMatch(t -> t >= 0));
    }
}
