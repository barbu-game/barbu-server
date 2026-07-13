package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.cluster.MatchmakingQueue;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class MatchmakingCoordinatorRankedIdempotencyTest {

    @Inject
    MatchmakingCoordinator coordinator;

    @Inject
    MatchmakingQueue queue;

    private static WebSocketSession sessionForUser(long userId) {
        FakeSession s = new FakeSession();
        s.put("userId", userId);
        return s;
    }

    @Test
    void two_tabs_of_the_same_account_hold_one_ranked_slot() {
        int before = queue.size();
        coordinator.enqueueRanked(sessionForUser(99L), "bob");
        coordinator.enqueueRanked(sessionForUser(99L), "bob");
        assertEquals(before + 1, queue.size(), "one account → at most one ranked queue slot");
    }
}
