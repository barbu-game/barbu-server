package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class EloPlacementsTest {

    @Test
    void abandoned_seats_are_ranked_below_everyone_who_stayed() {
        // Ordre par score décroissant : seat2, seat0, seat1, seat3 ; seat1 a abandonné.
        int[] placements = GameRoom.placementsForElo(List.of(2, 0, 1, 3), new boolean[] {false, true, false, false});
        // seat0->2, seat1->dernier(4), seat2->1, seat3->3
        assertArrayEquals(new int[] {2, 4, 1, 3}, placements);
    }

    @Test
    void when_everyone_abandoned_they_tie_for_last_so_elo_barely_moves() {
        int[] placements = GameRoom.placementsForElo(List.of(0, 1), new boolean[] {true, true});
        assertArrayEquals(new int[] {1, 1}, placements);
    }
}
