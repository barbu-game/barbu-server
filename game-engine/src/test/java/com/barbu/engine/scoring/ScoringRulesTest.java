package com.barbu.engine.scoring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void normalized_card_penalty_hands_out_exactly_sixty_over_the_matching_cards() {
        TrickOutcome o = new TrickOutcome(
                List.of(
                        List.of(c(Suit.SPADES, Rank.QUEEN), c(Suit.HEARTS, Rank.QUEEN)),
                        List.of(c(Suit.DIAMONDS, Rank.QUEEN)),
                        List.of(c(Suit.CLUBS, Rank.QUEEN))),
                List.of(0, 1, 2),
                3);
        TrickScoringRule rule = new NormalizedCardPenalty(Card::isQueen, "queen");
        int[] points = rule.score(o);
        assertArrayEquals(new int[] {-30, -15, -15}, points);
        assertEquals(-60, points[0] + points[1] + points[2]);
    }

    @Test
    void normalized_card_penalty_totals_sixty_even_when_the_count_does_not_divide_it() {
        // 13 hearts, all captured by one seat: 60 is not divisible by 13, yet the total is exact.
        List<Card> allHearts = new java.util.ArrayList<>();
        for (Rank r : Rank.values()) {
            allHearts.add(c(Suit.HEARTS, r));
        }
        TrickOutcome o = new TrickOutcome(List.of(allHearts, List.of()), List.of(0), 2);
        TrickScoringRule rule = new NormalizedCardPenalty(Card::isHeart, "heart");
        int[] points = rule.score(o);
        assertArrayEquals(new int[] {-60, 0}, points);
    }

    @Test
    void normalized_trick_penalty_hands_out_exactly_sixty_over_the_tricks() {
        TrickOutcome o =
                new TrickOutcome(List.of(List.of(), List.of(), List.of()), List.of(0, 0, 1, 1, 2, 2, 0, 1, 2, 0), 3);
        TrickScoringRule rule = new NormalizedTrickPenalty();
        int[] points = rule.score(o);
        assertEquals(-60, points[0] + points[1] + points[2]);
    }

    @Test
    void normalized_trick_penalty_charges_sixty_to_a_lone_taker() {
        TrickOutcome o = new TrickOutcome(List.of(List.of(), List.of()), List.of(0, 0, 0, 0, 0), 2);
        TrickScoringRule rule = new NormalizedTrickPenalty();
        assertArrayEquals(new int[] {-60, 0}, rule.score(o));
    }

    @Test
    void running_trick_penalty_shows_the_locked_in_minimum_not_the_lone_taker_maximum() {
        // One trick taken by seat 0; four tricks still to come (4 cards left in each hand).
        // Locked-in view spreads 60 over all 5 tricks → -12, not the -60 the captured-only view shows.
        TrickOutcome captured = new TrickOutcome(List.of(List.of(), List.of()), List.of(0), 2);
        List<List<Card>> remaining = List.of(
                List.of(
                        c(Suit.SPADES, Rank.TWO),
                        c(Suit.SPADES, Rank.THREE),
                        c(Suit.SPADES, Rank.FOUR),
                        c(Suit.SPADES, Rank.FIVE)),
                List.of(
                        c(Suit.CLUBS, Rank.TWO),
                        c(Suit.CLUBS, Rank.THREE),
                        c(Suit.CLUBS, Rank.FOUR),
                        c(Suit.CLUBS, Rank.FIVE)));
        TrickScoringRule rule = new NormalizedTrickPenalty();
        assertArrayEquals(new int[] {-12, 0}, rule.runningScore(captured, remaining));
    }

    @Test
    void running_trick_penalty_equals_final_score_once_the_round_is_over() {
        TrickOutcome full = new TrickOutcome(List.of(List.of(), List.of()), List.of(0, 0, 0, 0, 0), 2);
        TrickScoringRule rule = new NormalizedTrickPenalty();
        List<List<Card>> empty = List.of(List.of(), List.of());
        assertArrayEquals(rule.score(full), rule.runningScore(full, empty));
        assertArrayEquals(new int[] {-60, 0}, rule.runningScore(full, empty));
    }

    @Test
    void running_card_penalty_spreads_over_every_matching_card_in_play_not_only_those_captured() {
        // Two queens captured by seat 0, two still in hands. The locked-in view spreads 60 over all
        // four queens → -30, not the -60 a captured-only view would charge for two-of-two so far.
        TrickOutcome captured = new TrickOutcome(
                List.of(List.of(c(Suit.SPADES, Rank.QUEEN), c(Suit.HEARTS, Rank.QUEEN)), List.of()), List.of(0, 0), 2);
        List<List<Card>> remaining = List.of(List.of(c(Suit.DIAMONDS, Rank.QUEEN)), List.of(c(Suit.CLUBS, Rank.QUEEN)));
        TrickScoringRule rule = new NormalizedCardPenalty(Card::isQueen, "queen");
        assertArrayEquals(new int[] {-30, 0}, rule.runningScore(captured, remaining));
    }

    @Test
    void running_score_defaults_to_final_score_for_legacy_rules() {
        TrickOutcome o = new TrickOutcome(List.of(List.of(), List.of(), List.of()), List.of(0, 0, 2), 3);
        List<List<Card>> remaining = List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(), List.of());
        TrickScoringRule rule = new TrickPenalty(-2);
        assertArrayEquals(rule.score(o), rule.runningScore(o, remaining));
    }

    @Test
    void running_combined_rule_sums_the_running_score_of_each_component() {
        TrickOutcome captured =
                new TrickOutcome(List.of(List.of(c(Suit.HEARTS, Rank.QUEEN)), List.of()), List.of(0), 2);
        List<List<Card>> remaining = List.of(
                List.of(c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.TWO)),
                List.of(c(Suit.DIAMONDS, Rank.QUEEN), c(Suit.CLUBS, Rank.TWO)));
        TrickScoringRule rule = new CombinedRule(
                List.of(new NormalizedTrickPenalty(), new NormalizedCardPenalty(Card::isQueen, "queen")));
        int[] running = rule.runningScore(captured, remaining);
        // 3 tricks total → trick 0 worth -20 to seat 0; 3 queens total → seat 0's one queen worth -20.
        assertArrayEquals(new int[] {-40, 0}, running);
    }

    @Test
    void card_penalty_is_exhausted_once_no_matching_card_remains_in_hand() {
        TrickScoringRule rule = new CardPenalty(Card::isRedKing, -10, "red king");
        List<List<Card>> hands = List.of(
                List.of(c(Suit.SPADES, Rank.TWO)),
                List.of(c(Suit.CLUBS, Rank.THREE)),
                List.of(c(Suit.SPADES, Rank.ACE)));
        assertTrue(rule.exhausted(hands));
    }

    @Test
    void card_penalty_is_not_exhausted_while_a_matching_card_remains_in_hand() {
        TrickScoringRule rule = new CardPenalty(Card::isRedKing, -10, "red king");
        List<List<Card>> hands =
                List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(c(Suit.DIAMONDS, Rank.KING)), List.of());
        assertFalse(rule.exhausted(hands));
    }

    @Test
    void trick_penalty_is_never_exhausted_while_cards_remain() {
        TrickScoringRule rule = new TrickPenalty(-2);
        assertFalse(rule.exhausted(List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(), List.of())));
    }

    @Test
    void last_tricks_penalty_is_never_exhausted_early() {
        TrickScoringRule rule = new LastTricksPenalty(2, -10);
        assertFalse(rule.exhausted(List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(), List.of())));
    }

    @Test
    void combined_rule_is_exhausted_only_when_all_components_are() {
        List<List<Card>> noPenaltyCards =
                List.of(List.of(c(Suit.SPADES, Rank.TWO)), List.of(c(Suit.CLUBS, Rank.THREE)), List.of());
        TrickScoringRule withPerTrick =
                new CombinedRule(List.of(new TrickPenalty(-2), new CardPenalty(Card::isHeart, -2, "heart")));
        assertFalse(withPerTrick.exhausted(noPenaltyCards));
        TrickScoringRule onlyCardPenalties = new CombinedRule(
                List.of(new CardPenalty(Card::isHeart, -2, "heart"), new CardPenalty(Card::isQueen, -6, "queen")));
        assertTrue(onlyCardPenalties.exhausted(noPenaltyCards));
    }
}
