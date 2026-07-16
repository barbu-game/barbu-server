package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PauseVoteTest {

    // matchActive = game in progress, not finished; betweenRounds = match.round() == null.
    private static boolean eligibleAtBoundary() {
        return GameRoom.pauseAllowed(false, false, false, false, true, true, 2);
    }

    @Test
    void pause_is_allowed_between_rounds_with_humans_present() {
        assertTrue(eligibleAtBoundary());
    }

    @Test
    void pause_is_refused_mid_round() {
        // betweenRounds == false: a trick is in progress.
        assertFalse(GameRoom.pauseAllowed(false, false, false, false, true, false, 2));
    }

    @Test
    void pause_is_refused_when_a_stop_vote_is_open() {
        assertFalse(GameRoom.pauseAllowed(false, true, false, false, true, true, 2));
    }

    @Test
    void pause_is_refused_when_already_pausing_or_paused() {
        assertFalse(GameRoom.pauseAllowed(false, false, true, false, true, true, 2)); // pause vote already open
        assertFalse(GameRoom.pauseAllowed(false, false, false, true, true, true, 2)); // already paused
    }

    @Test
    void pause_is_refused_when_game_over_or_no_humans() {
        assertFalse(GameRoom.pauseAllowed(true, false, false, false, true, true, 2)); // game stopped
        assertFalse(GameRoom.pauseAllowed(false, false, false, false, false, true, 2)); // match inactive/finished
        assertFalse(GameRoom.pauseAllowed(false, false, false, false, true, true, 0)); // no human
    }

    @Test
    void pause_threshold_reuses_strict_majority() {
        assertTrue(GameRoom.stopVotePasses(2, 3));
        assertFalse(GameRoom.stopVotePasses(2, 4));
    }
}
