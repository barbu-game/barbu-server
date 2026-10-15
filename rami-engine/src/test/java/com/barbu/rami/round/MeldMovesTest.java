package com.barbu.rami.round;

import static com.barbu.rami.TestCards.c;
import static com.barbu.rami.TestCards.cs;
import static org.junit.jupiter.api.Assertions.*;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.meld.Meld;
import com.barbu.rami.meld.MeldKind;
import com.barbu.rami.round.RamiMove.Discard;
import com.barbu.rami.round.RamiMove.Draw;
import com.barbu.rami.round.RamiMove.LayOff;
import com.barbu.rami.round.RamiMove.NewMeld;
import com.barbu.rami.round.RamiMove.Open;
import com.barbu.rami.round.RamiMove.Source;
import com.barbu.rami.round.RamiMove.SwapJoker;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeldMovesTest {

    private static List<Integer> ids(List<RamiCard> cards) {
        return cards.stream().map(RamiCard::id).toList();
    }

    private static RamiRound play(List<RamiCard> hand, boolean opened, List<Meld> melds) {
        return RamiRoundEngine.setUp(
                0,
                Phase.PLAY,
                List.of(hand, cs("2C", "3C", "4C"), cs("2D", "3D", "4D"), cs("2S", "3S", "4S")),
                cs("9C", "9D"),
                cs("KC"),
                melds,
                List.of(opened, true, false, false));
    }

    private static List<RamiCard> concat(List<RamiCard> a, List<RamiCard> b) {
        List<RamiCard> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    @Test
    void opening_with_51_over_two_melds() {
        List<RamiCard> run = cs("JH", "QH", "KH");
        List<RamiCard> set = cs("7S", "7D", "7C");
        RamiCard spare = c("2C");
        List<RamiCard> hand = concat(concat(run, set), List.of(spare));
        RamiRound r = RamiRoundEngine.apply(play(hand, false, List.of()), 0, new Open(List.of(ids(run), ids(set))));
        assertTrue(r.opened().get(0));
        assertEquals(List.of(spare), r.hand(0));
        assertEquals(2, r.melds().size());
        assertEquals(new Meld(0, 0, MeldKind.RUN, run), r.melds().get(0));
    }

    @Test
    void opening_below_51_is_rejected() {
        List<RamiCard> run = cs("JH", "QH", "KH");
        List<RamiCard> set = cs("6S", "6D", "6C");
        RamiRound r = play(concat(concat(run, set), cs("2C")), false, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(r, 0, new Open(List.of(ids(run), ids(set)))));
    }

    @Test
    void opening_that_empties_the_hand_is_rejected() {
        List<RamiCard> run = cs("JH", "QH", "KH");
        List<RamiCard> set = cs("7S", "7D", "7C");
        RamiRound r = play(concat(run, set), false, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(r, 0, new Open(List.of(ids(run), ids(set)))));
    }

    @Test
    void opening_twice_or_melding_before_opening_is_rejected() {
        List<RamiCard> set = cs("7S", "7D", "7C");
        RamiRound opened = play(concat(set, cs("2C")), true, List.of());
        assertThrows(
                IllegalArgumentException.class, () -> RamiRoundEngine.apply(opened, 0, new Open(List.of(ids(set)))));
        RamiRound closed = play(concat(set, cs("2C")), false, List.of());
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(closed, 0, new NewMeld(ids(set))));
    }

    @Test
    void new_meld_after_opening() {
        List<RamiCard> set = cs("7S", "7D", "7C");
        RamiRound r = RamiRoundEngine.apply(play(concat(set, cs("2C")), true, List.of()), 0, new NewMeld(ids(set)));
        assertEquals(1, r.melds().size());
        assertEquals(1, r.hand(0).size());
    }

    @Test
    void lay_off_on_an_opponent_run_at_either_end() {
        Meld meld = new Meld(4, 1, MeldKind.RUN, cs("5H", "6H", "7H"));
        RamiCard four = c("4H");
        RamiCard eight = c("8H");
        RamiRound r = play(List.of(four, eight, c("2C")), true, List.of(meld));
        RamiRound after = RamiRoundEngine.apply(r, 0, new LayOff(4, List.of(four.id()), List.of(eight.id())));
        Meld extended = after.melds().get(0);
        assertEquals(1, extended.owner());
        assertEquals(four, extended.cards().get(0));
        assertEquals(eight, extended.cards().get(4));
        assertEquals(1, after.hand(0).size());
    }

    @Test
    void lay_off_requires_opening_a_fitting_card_and_a_kept_card() {
        Meld meld = new Meld(4, 1, MeldKind.RUN, cs("5H", "6H", "7H"));
        RamiCard eight = c("8H");
        RamiCard nine = c("9S");
        RamiRound closed = play(List.of(eight, nine), false, List.of(meld));
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(closed, 0, new LayOff(4, List.of(), List.of(eight.id()))));
        RamiRound opened = play(List.of(eight, nine), true, List.of(meld));
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(opened, 0, new LayOff(4, List.of(), List.of(nine.id()))));
        RamiRound lastCard = play(List.of(eight), true, List.of(meld));
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(lastCard, 0, new LayOff(4, List.of(), List.of(eight.id()))));
    }

    @Test
    void second_joker_cannot_join_a_meld() {
        Meld meld = new Meld(4, 1, MeldKind.RUN, cs("5H", "6H", "JK"));
        RamiCard joker = c("JK");
        RamiRound r = play(List.of(joker, c("2C")), true, List.of(meld));
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(r, 0, new LayOff(4, List.of(), List.of(joker.id()))));
    }

    @Test
    void swapping_a_joker_puts_it_in_hand_and_it_must_be_replayed() {
        List<RamiCard> run = cs("5H", "6H", "JK");
        RamiCard joker = run.get(2);
        RamiCard seven = c("7H");
        RamiCard two = c("2C");
        List<RamiCard> pair = cs("9S", "9D");
        RamiRound r = play(concat(List.of(seven, two), pair), true, List.of(new Meld(4, 1, MeldKind.RUN, run)));
        RamiRound swapped = RamiRoundEngine.apply(r, 0, new SwapJoker(4, seven.id()));
        assertEquals(seven, swapped.melds().get(0).cards().get(2));
        assertTrue(swapped.hand(0).contains(joker));
        assertTrue(swapped.mustPlay().contains(joker.id()));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(swapped, 0, new Discard(two.id())));
        RamiRound replayed = RamiRoundEngine.apply(
                swapped, 0, new NewMeld(List.of(pair.get(0).id(), pair.get(1).id(), joker.id())));
        assertTrue(replayed.mustPlay().isEmpty());
    }

    @Test
    void swap_with_the_wrong_card_or_a_duplicate_suit_is_rejected() {
        RamiCard eight = c("8H");
        RamiRound run =
                play(List.of(eight, c("2C")), true, List.of(new Meld(4, 1, MeldKind.RUN, cs("5H", "6H", "JK"))));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(run, 0, new SwapJoker(4, eight.id())));
        RamiCard otherSevenOfHearts = c("7H");
        RamiRound set = play(
                List.of(otherSevenOfHearts, c("2C")),
                true,
                List.of(new Meld(4, 1, MeldKind.SET, cs("7H", "7S", "JK"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(set, 0, new SwapJoker(4, otherSevenOfHearts.id())));
    }

    @Test
    void swap_before_opening_is_rejected() {
        RamiCard seven = c("7H");
        RamiRound r = play(List.of(seven, c("2C")), false, List.of(new Meld(4, 1, MeldKind.RUN, cs("5H", "6H", "JK"))));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(r, 0, new SwapJoker(4, seven.id())));
    }

    @Test
    void taken_discard_can_fund_the_opening() {
        RamiCard king = c("KH");
        List<RamiCard> hand = concat(cs("JH", "QH", "7S", "7D", "7C"), cs("2C", "3C"));
        RamiRound start = RamiRoundEngine.setUp(
                0,
                Phase.DRAW,
                List.of(hand, cs("2C", "3C", "4C"), cs("2D", "3D", "4D"), cs("2S", "3S", "4S")),
                cs("9C"),
                List.of(king),
                List.of(),
                List.of(false, false, false, false));
        RamiRound drawn = RamiRoundEngine.apply(start, 0, new Draw(Source.DISCARD));
        RamiRound opened = RamiRoundEngine.apply(
                drawn,
                0,
                new Open(List.of(
                        List.of(hand.get(0).id(), hand.get(1).id(), king.id()),
                        List.of(hand.get(2).id(), hand.get(3).id(), hand.get(4).id()))));
        assertTrue(opened.mustPlay().isEmpty());
    }

    @Test
    void rami_sec_when_the_winner_had_not_opened_at_turn_start() {
        List<RamiCard> run = cs("JH", "QH", "KH");
        List<RamiCard> set = cs("7S", "7D", "7C");
        RamiCard spare = c("2C");
        RamiRound r = play(concat(concat(run, set), List.of(spare)), false, List.of());
        r = RamiRoundEngine.apply(r, 0, new Open(List.of(ids(run), ids(set))));
        r = RamiRoundEngine.apply(r, 0, new Discard(spare.id()));
        assertEquals(0, r.winner());
        assertTrue(r.ramiSec());

        RamiCard last = c("5S");
        RamiRound normal = RamiRoundEngine.apply(play(List.of(last), true, List.of()), 0, new Discard(last.id()));
        assertFalse(normal.ramiSec());
    }
}
