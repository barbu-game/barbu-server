package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EventChatTest {

    @Test
    void elo_line_shows_a_signed_gain() {
        assertEquals("Alice : 1200 → 1215 (+15)", GameRoom.eloChatLine("Alice", 1200, 1215, 15));
    }

    @Test
    void elo_line_shows_a_loss_with_minus_sign() {
        assertEquals("Bob : 1200 → 1188 (-12)", GameRoom.eloChatLine("Bob", 1200, 1188, -12));
    }

    @Test
    void elo_line_shows_a_zero_delta_as_plus_zero() {
        assertEquals("Cara : 1200 → 1200 (+0)", GameRoom.eloChatLine("Cara", 1200, 1200, 0));
    }
}
