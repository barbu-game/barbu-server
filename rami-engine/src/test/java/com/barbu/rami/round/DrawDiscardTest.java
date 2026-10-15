package com.barbu.rami.round;

import static com.barbu.rami.TestCards.c;
import static com.barbu.rami.TestCards.cs;
import static org.junit.jupiter.api.Assertions.*;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.round.RamiMove.Discard;
import com.barbu.rami.round.RamiMove.Draw;
import com.barbu.rami.round.RamiMove.ResetTurn;
import com.barbu.rami.round.RamiMove.Source;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class DrawDiscardTest {

    private static RamiRound seat0(Phase phase, List<RamiCard> hand, List<RamiCard> stock, List<RamiCard> discard) {
        return RamiRoundEngine.setUp(
                0,
                phase,
                List.of(hand, cs("2C", "3C", "4C"), cs("2D", "3D", "4D"), cs("2S", "3S", "4S")),
                stock,
                discard,
                List.of(),
                Collections.nCopies(4, false));
    }

    @Test
    void draw_from_stock_takes_its_top_and_enters_play() {
        List<RamiCard> stock = cs("9H", "10H");
        RamiRound r = RamiRoundEngine.apply(
                seat0(Phase.DRAW, cs("5S", "6S", "7S"), stock, cs("KD")), 0, new Draw(Source.STOCK));
        assertEquals(Phase.PLAY, r.phase());
        assertTrue(r.hand(0).contains(stock.get(0)));
        assertEquals(List.of(stock.get(1)), r.stock());
    }

    @Test
    void taken_discard_must_be_played_before_discarding() {
        RamiCard top = c("KD");
        RamiRound r = RamiRoundEngine.apply(
                seat0(Phase.DRAW, cs("5S", "6S", "7S"), cs("9H"), List.of(top)), 0, new Draw(Source.DISCARD));
        assertEquals(java.util.Set.of(top.id()), r.mustPlay());
        RamiRound after = r;
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(after, 0, new Discard(top.id())));
    }

    @Test
    void discard_pile_is_closed_with_two_cards_or_fewer() {
        RamiRound r = seat0(Phase.DRAW, cs("5S", "6S"), cs("9H"), cs("KD"));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(r, 0, new Draw(Source.DISCARD)));
    }

    @Test
    void empty_discard_pile_cannot_be_taken() {
        RamiRound r = seat0(Phase.DRAW, cs("5S", "6S", "7S"), cs("9H"), List.of());
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(r, 0, new Draw(Source.DISCARD)));
    }

    @Test
    void out_of_turn_and_wrong_phase_are_rejected() {
        RamiRound r = seat0(Phase.DRAW, cs("5S", "6S", "7S"), cs("9H"), cs("KD"));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(r, 1, new Draw(Source.STOCK)));
        assertThrows(
                IllegalArgumentException.class,
                () -> RamiRoundEngine.apply(r, 0, new Discard(r.hand(0).get(0).id())));
        RamiRound play = seat0(Phase.PLAY, cs("5S", "6S", "7S"), cs("9H"), cs("KD"));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(play, 0, new Draw(Source.STOCK)));
    }

    @Test
    void empty_stock_recycles_the_discard_under_its_top_without_shuffling() {
        List<RamiCard> discard = cs("2H", "3H", "4H");
        RamiRound r = RamiRoundEngine.apply(
                seat0(Phase.DRAW, cs("5S", "6S", "7S"), List.of(), discard), 0, new Draw(Source.STOCK));
        assertTrue(r.hand(0).contains(discard.get(0)));
        assertEquals(List.of(discard.get(1)), r.stock());
        assertEquals(List.of(discard.get(2)), r.discard());
    }

    @Test
    void a_stock_recycled_too_often_ends_the_round_without_winner() {
        RamiRound fresh = seat0(Phase.DRAW, cs("5S", "6S", "7S"), List.of(), cs("2H", "3H", "4H"));
        assertEquals(1, RamiRoundEngine.apply(fresh, 0, new Draw(Source.STOCK)).recycles());
        RamiRound worn = new RamiRound(
                fresh.hands(),
                fresh.stock(),
                fresh.discard(),
                fresh.melds(),
                fresh.opened(),
                0,
                Phase.DRAW,
                java.util.Set.of(),
                false,
                fresh.turnStart(),
                0,
                null,
                RamiRoundEngine.MAX_RECYCLES);
        RamiRound r = RamiRoundEngine.apply(worn, 0, new Draw(Source.STOCK));
        assertEquals(Phase.FINISHED, r.phase());
        assertNull(r.winner());
    }

    @Test
    void nothing_left_to_draw_ends_the_round_without_winner() {
        RamiRound r = RamiRoundEngine.apply(
                seat0(Phase.DRAW, cs("5S", "6S", "7S"), List.of(), cs("2H")), 0, new Draw(Source.STOCK));
        assertEquals(Phase.FINISHED, r.phase());
        assertNull(r.winner());
        assertFalse(r.ramiSec());
    }

    @Test
    void discard_passes_the_turn() {
        RamiCard king = c("KD");
        RamiRound r = RamiRoundEngine.apply(
                seat0(Phase.PLAY, List.of(c("5S"), king), cs("9H"), cs("2H")), 0, new Discard(king.id()));
        assertEquals(1, r.current());
        assertEquals(Phase.DRAW, r.phase());
        assertEquals(king, r.discard().get(r.discard().size() - 1));
        assertEquals(r.turnStart().hand(1), r.hand(1));
    }

    @Test
    void discarding_the_last_card_wins_the_round() {
        RamiCard last = c("5S");
        RamiRound r =
                RamiRoundEngine.apply(seat0(Phase.PLAY, List.of(last), cs("9H"), cs("2H")), 0, new Discard(last.id()));
        assertEquals(Phase.FINISHED, r.phase());
        assertEquals(0, r.winner());
        assertThrows(IllegalStateException.class, () -> RamiRoundEngine.apply(r, 1, new Draw(Source.STOCK)));
    }

    @Test
    void reset_after_taking_the_discard_restores_the_turn_and_closes_the_discard() {
        RamiCard top = c("KD");
        RamiRound start = seat0(Phase.DRAW, cs("5S", "6S", "7S"), cs("9H"), List.of(top));
        RamiRound taken = RamiRoundEngine.apply(start, 0, new Draw(Source.DISCARD));
        RamiRound reset = RamiRoundEngine.apply(taken, 0, new ResetTurn());
        assertEquals(Phase.DRAW, reset.phase());
        assertEquals(start.hand(0), reset.hand(0));
        assertEquals(List.of(top), reset.discard());
        assertTrue(reset.stockOnly());
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(reset, 0, new Draw(Source.DISCARD)));
        assertEquals(
                Phase.PLAY,
                RamiRoundEngine.apply(reset, 0, new Draw(Source.STOCK)).phase());
    }

    @Test
    void reset_after_a_stock_draw_keeps_the_drawn_card() {
        List<RamiCard> stock = cs("9H", "10H");
        RamiRound drawn = RamiRoundEngine.apply(
                seat0(Phase.DRAW, cs("5S", "6S", "7S"), stock, cs("KD")), 0, new Draw(Source.STOCK));
        RamiRound reset = RamiRoundEngine.apply(drawn, 0, new ResetTurn());
        assertEquals(Phase.PLAY, reset.phase());
        assertTrue(reset.hand(0).contains(stock.get(0)));
        assertEquals(List.of(stock.get(1)), reset.stock());
    }

    @Test
    void reset_is_only_allowed_in_play_phase() {
        RamiRound r = seat0(Phase.DRAW, cs("5S", "6S", "7S"), cs("9H"), cs("KD"));
        assertThrows(IllegalArgumentException.class, () -> RamiRoundEngine.apply(r, 0, new ResetTurn()));
    }
}
