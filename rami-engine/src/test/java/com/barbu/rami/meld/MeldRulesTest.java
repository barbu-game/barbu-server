package com.barbu.rami.meld;

import static com.barbu.rami.TestCards.cs;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class MeldRulesTest {

    private static Optional<MeldKind> kind(String... codes) {
        return MeldRules.kind(cs(codes));
    }

    private static int points(String... codes) {
        return MeldRules.points(cs(codes));
    }

    @Test
    void runs_of_three_or_more_same_suit() {
        assertEquals(Optional.of(MeldKind.RUN), kind("5H", "6H", "7H"));
        assertEquals(18, points("5H", "6H", "7H"));
        assertEquals(Optional.of(MeldKind.RUN), kind("9S", "10S", "JS", "QS", "KS"));
    }

    @Test
    void joker_takes_the_value_of_its_position() {
        assertEquals(18, points("5H", "6H", "JK"));
        assertEquals(18, points("5H", "JK", "7H"));
        assertEquals(15, points("JK", "5H", "6H"));
        assertEquals(30, points("JH", "QH", "JK"));
    }

    @Test
    void ace_is_low_in_a23_and_high_in_qka() {
        assertEquals(6, points("AH", "2H", "3H"));
        assertEquals(31, points("QH", "KH", "AH"));
    }

    @Test
    void runs_do_not_wrap_and_joker_cannot_extend_past_an_ace() {
        assertTrue(kind("KH", "AH", "2H").isEmpty());
        assertTrue(kind("JK", "AH", "2H").isEmpty());
        assertTrue(kind("QH", "KH", "AH", "JK").isEmpty());
    }

    @Test
    void ace_cannot_close_both_ends_of_a_run() {
        assertEquals(
                Optional.of(MeldKind.RUN),
                kind("AH", "2H", "3H", "4H", "5H", "6H", "7H", "8H", "9H", "10H", "JH", "QH", "KH"));
        assertTrue(kind("AH", "2H", "3H", "4H", "5H", "6H", "7H", "8H", "9H", "10H", "JH", "QH", "KH", "AH")
                .isEmpty());
        assertTrue(kind("JK", "2H", "3H", "4H", "5H", "6H", "7H", "8H", "9H", "10H", "JH", "QH", "KH", "AH")
                .isEmpty());
    }

    @Test
    void run_rejects_mixed_suits_wrong_order_and_two_jokers() {
        assertTrue(kind("5H", "6S", "7H").isEmpty());
        assertTrue(kind("7H", "5H", "6H").isEmpty());
        assertTrue(kind("5H", "JK", "JK").isEmpty());
        assertTrue(kind("5H", "6H").isEmpty());
    }

    @Test
    void sets_of_three_or_four_distinct_suits() {
        assertEquals(Optional.of(MeldKind.SET), kind("7H", "7S", "7D"));
        assertEquals(21, points("7H", "7S", "7D"));
        assertEquals(28, points("7H", "7S", "7D", "7C"));
        assertEquals(21, points("7H", "7S", "JK"));
        assertEquals(33, points("AH", "AS", "AD"));
        assertEquals(30, points("KH", "KS", "KD"));
    }

    @Test
    void set_rejects_duplicate_suit_five_cards_and_mixed_ranks() {
        assertTrue(kind("7H", "7H", "7S").isEmpty());
        assertTrue(kind("7H", "7S", "7D", "7C", "JK").isEmpty());
        assertTrue(kind("7H", "8S", "7D").isEmpty());
        assertTrue(kind("7H", "7S", "JK", "JK").isEmpty());
    }

    @Test
    void points_of_an_invalid_meld_throws() {
        assertThrows(IllegalArgumentException.class, () -> points("5H", "6S", "7H"));
    }
}
