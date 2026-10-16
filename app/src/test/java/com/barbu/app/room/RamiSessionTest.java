package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.protocol.GameStateMessage.PlayerInfo;
import com.barbu.app.protocol.GameStateMessage.VoteState;
import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RamiMatchEngine;
import java.util.List;
import org.junit.jupiter.api.Test;

class RamiSessionTest {

    private static RamiMatch between(int roundsPlayed, int total) {
        return new RamiMatch(4, total, 1L, roundsPlayed, List.of(0, 0, 0, 0), null, null);
    }

    @Test
    void stop_vote_is_offered_between_rounds_but_not_before_the_first_or_after_the_last() {
        assertFalse(new RamiSession(4, between(0, 4)).atStopVoteBoundary());
        assertTrue(new RamiSession(4, between(1, 4)).atStopVoteBoundary());
        assertTrue(new RamiSession(4, between(3, 4)).atStopVoteBoundary());
        assertFalse(new RamiSession(4, between(4, 4)).atStopVoteBoundary());
        assertFalse(new RamiSession(4, RamiMatchEngine.startNextRound(between(0, 4))).atStopVoteBoundary());
    }

    @Test
    void the_view_carries_the_stop_vote() {
        VoteState vote = new VoteState(true, 1, 0, null);
        RoomContext ctx = new RoomContext(
                "R",
                4,
                0,
                null,
                "CONTRACT_SELECTION",
                List.of(new PlayerInfo(0, "A", false, true)),
                new String[] {"A", "B", "C", "D"},
                false,
                vote,
                null,
                null,
                null);
        assertEquals(vote, new RamiSession(4, between(1, 4)).view(ctx).stopVote());
    }
}
