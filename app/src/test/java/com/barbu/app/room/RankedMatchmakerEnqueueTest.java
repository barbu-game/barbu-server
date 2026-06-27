package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class RankedMatchmakerEnqueueTest {

    @Inject
    RankedMatchmaker matchmaker;

    @Test
    void enqueuing_the_same_session_twice_takes_a_single_queue_slot() {
        WebSocketSession session = sessionForUser(4242L);
        int before = matchmaker.queuedCount();
        matchmaker.enqueue(session, "alice", 0);
        matchmaker.enqueue(session, "alice", 0);
        assertEquals(before + 1, matchmaker.queuedCount(), "a repeated click must not enqueue the account twice");
        matchmaker.cancel(session);
    }

    @Test
    void two_tabs_of_the_same_account_cannot_both_queue() {
        WebSocketSession tabA = sessionForUser(99L);
        WebSocketSession tabB = sessionForUser(99L);
        int before = matchmaker.queuedCount();
        matchmaker.enqueue(tabA, "bob", 0);
        matchmaker.enqueue(tabB, "bob", 0);
        assertEquals(before + 1, matchmaker.queuedCount(), "one account holds at most one ranked queue slot");
        matchmaker.cancel(tabA);
        matchmaker.cancel(tabB);
    }

    private static WebSocketSession sessionForUser(long userId) {
        FakeSession s = new FakeSession();
        s.put("userId", userId);
        return s;
    }
}
