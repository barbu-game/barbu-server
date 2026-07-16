package com.barbu.app.rating;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.rating.EloCalculator.Participant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    private final EloCalculator calc = new EloCalculator(EloConfig.defaults());

    private List<Integer> deltas(List<Participant> ps) {
        return calc.compute(ps).stream().map(EloCalculator.Delta::ratingDelta).toList();
    }

    @Test
    void two_equal_players_winner_gains_half_k_loser_loses_it() {
        // K=32, equal ratings → expected 0.5; winner placement 1, loser 2.
        List<Integer> d = deltas(List.of(
                new Participant(1000, 50, 1), // gamesPlayed 50 → non-provisional (K=32)
                new Participant(1000, 50, 2)));
        assertEquals(16, d.get(0));
        assertEquals(-16, d.get(1));
    }

    @Test
    void favorite_winning_gains_less_than_underdog_winning() {
        int favoriteWins = deltas(List.of(new Participant(1300, 50, 1), new Participant(1000, 50, 2)))
                .get(0);
        int underdogWins = deltas(List.of(new Participant(1000, 50, 1), new Participant(1300, 50, 2)))
                .get(0);
        assertTrue(favoriteWins < underdogWins, favoriteWins + " < " + underdogWins);
        assertTrue(favoriteWins > 0);
    }

    @Test
    void equal_ratings_deltas_are_strictly_decreasing_by_placement() {
        List<Integer> d = deltas(List.of(
                new Participant(1000, 50, 1),
                new Participant(1000, 50, 2),
                new Participant(1000, 50, 3),
                new Participant(1000, 50, 4)));
        assertTrue(d.get(0) > d.get(1));
        assertTrue(d.get(1) > d.get(2));
        assertTrue(d.get(2) > d.get(3));
    }

    @Test
    void tied_placement_yields_zero_delta_for_equal_ratings() {
        List<Integer> d = deltas(List.of(new Participant(1000, 50, 1), new Participant(1000, 50, 1)));
        assertEquals(0, d.get(0));
        assertEquals(0, d.get(1));
    }

    @Test
    void provisional_player_moves_more_than_veteran_in_same_spot() {
        int veteran = deltas(List.of(new Participant(1000, 50, 2), new Participant(1000, 50, 1)))
                .get(0);
        int rookie = deltas(List.of(new Participant(1000, 0, 2), new Participant(1000, 50, 1)))
                .get(0);
        assertTrue(Math.abs(rookie) > Math.abs(veteran), rookie + " vs " + veteran);
    }

    @Test
    void sum_of_deltas_is_near_zero_for_equal_k() {
        // At equal K (all non-provisional), the unrounded sum is 0; integer rounding bounds the
        // error to ~N/2. We check the zero-sum property over random configurations.
        Random rng = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            int n = 2 + rng.nextInt(9); // 2..10
            List<Participant> ps = new ArrayList<>();
            List<Integer> placements = new ArrayList<>();
            for (int i = 1; i <= n; i++) placements.add(i);
            java.util.Collections.shuffle(placements, rng);
            for (int i = 0; i < n; i++) {
                ps.add(new Participant(800 + rng.nextInt(800), 50, placements.get(i)));
            }
            int total = deltas(ps).stream().mapToInt(Integer::intValue).sum();
            assertTrue(Math.abs(total) <= n, "n=" + n + " total=" + total);
        }
    }

    @Test
    void single_participant_yields_no_change() {
        assertEquals(List.of(0), deltas(List.of(new Participant(1000, 50, 1))));
    }
}
