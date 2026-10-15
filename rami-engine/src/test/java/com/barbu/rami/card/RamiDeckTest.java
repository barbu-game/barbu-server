package com.barbu.rami.card;

import static com.barbu.rami.TestCards.c;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.card.Card;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RamiDeckTest {

    @Test
    void has_108_cards_unique_ids_four_jokers_and_two_copies_of_each_card() {
        List<RamiCard> deck = RamiDeck.shuffled(1);
        assertEquals(RamiDeck.SIZE, deck.size());
        assertEquals(108, deck.stream().map(RamiCard::id).distinct().count());
        assertEquals(4, deck.stream().filter(RamiCard::isJoker).count());
        Map<Card, Long> copies =
                deck.stream().filter(x -> !x.isJoker()).collect(groupingBy(RamiCard::card, counting()));
        assertEquals(52, copies.size());
        assertTrue(copies.values().stream().allMatch(n -> n == 2));
    }

    @Test
    void shuffle_is_deterministic_per_seed() {
        assertEquals(RamiDeck.shuffled(7), RamiDeck.shuffled(7));
        assertNotEquals(RamiDeck.shuffled(7), RamiDeck.shuffled(8));
    }

    @Test
    void penalties_follow_the_scale() {
        assertEquals(25, c("JK").penalty());
        assertEquals(11, c("AS").penalty());
        assertEquals(10, c("KH").penalty());
        assertEquals(10, c("JD").penalty());
        assertEquals(10, c("10C").penalty());
        assertEquals(7, c("7H").penalty());
    }
}
