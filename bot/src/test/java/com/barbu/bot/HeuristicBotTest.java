package com.barbu.bot;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.Trick;
import com.barbu.engine.round.TrickTakingState;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class HeuristicBotTest {
    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    private final HeuristicBot bot = new HeuristicBot();

    @Property
    void every_chosen_move_is_legal_through_a_full_match(
            @ForAll @IntRange(min = 2, max = 10) int n, @ForAll @LongRange(min = 0, max = 150) long seed) {
        MatchState m = MatchEngine.newMatch(n, seed);
        while (!MatchEngine.isComplete(m)) {
            m = MatchEngine.startNextContract(m);
            while (m.round() != null) {
                RoundState round = m.round();
                int seat = round.currentPlayer();
                Move move = bot.chooseMove(round, seat);
                assertTrue(
                        RoundEngine.legalMoves(round, seat).contains(move),
                        "illegal bot move n=" + n + " seed=" + seed);
                m = MatchEngine.applyMove(m, seat, move);
            }
        }
        assertEquals(5 * n, m.roundNumber());
    }

    @Test
    void sheds_a_heart_when_it_can_lose_under_no_hearts() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.ACE)),
                List.of(c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.TWO)),
                List.of(),
                List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_HEARTS,
                hands,
                Trick.startedBy(0, 4).withCard(c(Suit.SPADES, Rank.ACE)),
                List.of(List.of(), List.of(), List.of(), List.of()),
                List.of(),
                1);

        Move move = bot.chooseMove(s, 1);
        assertEquals(new Move.PlayCard(c(Suit.HEARTS, Rank.KING)), move);
    }

    @Test
    void takes_a_penalty_free_trick_to_shed_a_high_card_under_no_hearts() {
        // Heads-up: seat 1 is last to play onto a hearts-free trick, so taking it costs
        // nothing — and should be preferred, dumping the dangerous Ace rather than the deuce.
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.KING)), List.of(c(Suit.SPADES, Rank.ACE), c(Suit.SPADES, Rank.TWO)));
        TrickTakingState s = new TrickTakingState(
                Contract.NO_HEARTS,
                hands,
                Trick.startedBy(0, 2).withCard(c(Suit.SPADES, Rank.KING)),
                List.of(List.of(), List.of()),
                List.of(),
                1);

        Move move = bot.chooseMove(s, 1);
        assertEquals(new Move.PlayCard(c(Suit.SPADES, Rank.ACE)), move);
    }

    @Test
    void still_loses_a_trick_holding_a_heart_under_no_hearts() {
        // seat 1 discarded a heart into the trick, so seat 2 (last) must duck under the King
        // rather than scoop up the heart, even though taking is otherwise possible.
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.KING)),
                List.of(c(Suit.HEARTS, Rank.FIVE)),
                List.of(c(Suit.SPADES, Rank.ACE), c(Suit.SPADES, Rank.TWO)));
        TrickTakingState s = new TrickTakingState(
                Contract.NO_HEARTS,
                hands,
                Trick.startedBy(0, 3).withCard(c(Suit.SPADES, Rank.KING)).withCard(c(Suit.HEARTS, Rank.FIVE)),
                List.of(List.of(), List.of(), List.of()),
                List.of(),
                2);

        Move move = bot.chooseMove(s, 2);
        assertEquals(new Move.PlayCard(c(Suit.SPADES, Rank.TWO)), move);
    }

    @Test
    void still_ducks_the_last_card_under_no_tricks() {
        // Every trick is a penalty under NO_TRICKS, so the free-take heuristic must not apply:
        // seat 1, last to play, ducks under the King instead of grabbing the trick.
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.KING)), List.of(c(Suit.SPADES, Rank.ACE), c(Suit.SPADES, Rank.TWO)));
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS,
                hands,
                Trick.startedBy(0, 2).withCard(c(Suit.SPADES, Rank.KING)),
                List.of(List.of(), List.of()),
                List.of(),
                1);

        Move move = bot.chooseMove(s, 1);
        assertEquals(new Move.PlayCard(c(Suit.SPADES, Rank.TWO)), move);
    }

    @Test
    void avoids_winning_the_trick_under_no_tricks() {
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.TEN)),
                List.of(c(Suit.SPADES, Rank.TWO), c(Suit.SPADES, Rank.KING)),
                List.of(),
                List.of());
        TrickTakingState s = new TrickTakingState(
                Contract.NO_TRICKS,
                hands,
                Trick.startedBy(0, 4).withCard(c(Suit.SPADES, Rank.TEN)),
                List.of(List.of(), List.of(), List.of(), List.of()),
                List.of(),
                1);

        Move move = bot.chooseMove(s, 1);
        assertEquals(new Move.PlayCard(c(Suit.SPADES, Rank.TWO)), move);
    }
}
