package com.barbu.app.room;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StopVoteTest {

    @Test
    void a_strict_majority_of_humans_stops_the_game() {
        assertTrue(GameRoom.stopVotePasses(2, 3));   // 2 of 3
        assertTrue(GameRoom.stopVotePasses(3, 4));   // 3 of 4
        assertTrue(GameRoom.stopVotePasses(1, 1));   // solo human
    }

    @Test
    void a_tie_or_minority_keeps_playing() {
        assertFalse(GameRoom.stopVotePasses(2, 4));  // exact half
        assertFalse(GameRoom.stopVotePasses(1, 3));
        assertFalse(GameRoom.stopVotePasses(0, 2));
    }
}
