package com.barbu.engine.scoring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringRulesTest {

    private static Card c(Suit s, Rank r) {
        return new Card(s, r);
    }

    @Test
    void card_penalty_counts_matching_cards_per_seat() {
        TrickOutcome o = new TrickOutcome(
                List.of(
                        List.of(c(Suit.HEARTS, Rank.TWO), c(Suit.HEARTS, Rank.KING)),
                        List.of(c(Suit.HEARTS, Rank.FIVE), c(Suit.SPADES, Rank.ACE)),
                        List.of(c(Suit.CLUBS, Rank.NINE))),
                List.of(0, 1, 2),
                3);
        TrickScoringRule rule = new CardPenalty(Card::isHeart, -2, "heart");
        assertArrayEquals(new int[] {-4, -2, 0}, rule.score(o));
    }

    @Test
    void trick_penalty_counts_tricks_taken_per_seat() {
        TrickOutcome o = new TrickOutcome(List.of(List.of(), List.of(), List.of()), List.of(0, 0, 2), 3);
        TrickScoringRule rule = new TrickPenalty(-2);
        assertArrayEquals(new int[] {-4, 0, -2}, rule.score(o));
    }

    @Test
    void last_tricks_penalty_only_hits_takers_of_the_last_n() {
        TrickOutcome o = new TrickOutcome(List.of(List.of(), List.of(), List.of()), List.of(1, 2, 0, 2), 3);
        TrickScoringRule rule = new LastTricksPenalty(2, -10);
        assertArrayEquals(new int[] {-10, 0, -10}, rule.score(o));
    }

    @Test
    void combined_rule_sums_components_and_describes_each() {
        TrickOutcome o =
                new TrickOutcome(List.of(List.of(c(Suit.HEARTS, Rank.TWO)), List.of(), List.of()), List.of(0, 1, 2), 3);
        TrickScoringRule rule =
                new CombinedRule(List.of(new TrickPenalty(-2), new CardPenalty(Card::isHeart, -2, "heart")));
        assertArrayEquals(new int[] {-4, -2, -2}, rule.score(o));
        assertTrue(rule.describe().contains("trick"));
        assertTrue(rule.describe().contains("heart"));
    }
}
