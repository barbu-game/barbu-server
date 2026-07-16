package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReclaimableSeatTest {

    // 4 seats. occupied[s] = a session is active on this seat.
    private final Long[] userIds = {10L, 20L, null, 30L};
    private final String[] tokens = {"t0", "t1", "t2", "t3"};

    @Test
    void reclaims_a_disconnected_seat_by_token() {
        boolean[] occupied = {true, false, false, true};
        assertEquals(1, GameRoom.reclaimableSeat(null, "t1", userIds, tokens, occupied));
    }

    @Test
    void reclaims_a_disconnected_seat_by_user_id() {
        boolean[] occupied = {true, true, false, false};
        assertEquals(3, GameRoom.reclaimableSeat(30L, null, userIds, tokens, occupied));
    }

    @Test
    void token_wins_over_user_id_when_both_match_different_seats() {
        boolean[] occupied = {false, false, false, false};
        // userId 10 → seat 0; token t3 → seat 3. The token wins.
        assertEquals(3, GameRoom.reclaimableSeat(10L, "t3", userIds, tokens, occupied));
    }

    @Test
    void ignores_an_occupied_seat_even_if_identity_matches() {
        boolean[] occupied = {true, true, true, true};
        assertEquals(-1, GameRoom.reclaimableSeat(10L, "t0", userIds, tokens, occupied));
    }

    @Test
    void returns_minus_one_for_a_stranger() {
        boolean[] occupied = {false, false, false, false};
        assertEquals(-1, GameRoom.reclaimableSeat(999L, "unknown", userIds, tokens, occupied));
    }
}
