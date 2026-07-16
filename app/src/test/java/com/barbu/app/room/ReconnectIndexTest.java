package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReconnectIndexTest {

    @Test
    void resolves_room_by_user_and_token() {
        ReconnectIndex index = new InMemoryReconnectIndex();
        index.register(7L, "tok-a", "ROOM1");
        assertEquals("ROOM1", index.roomForUser(7L));
        assertEquals("ROOM1", index.roomForToken("tok-a"));
        assertNull(index.roomForUser(99L));
        assertNull(index.roomForToken("nope"));
    }

    @Test
    void register_with_null_user_only_indexes_the_token() {
        ReconnectIndex index = new InMemoryReconnectIndex();
        index.register(null, "guest-tok", "ROOM2");
        assertEquals("ROOM2", index.roomForToken("guest-tok"));
    }

    @Test
    void forget_is_conditional_on_the_room_id() {
        ReconnectIndex index = new InMemoryReconnectIndex();
        index.register(7L, "tok-a", "ROOM1");
        // The player joins another room: their entry now points to ROOM2.
        index.register(7L, "tok-b", "ROOM2");
        // ROOM1 is GC'd and tries to forget the old entry — must NOT touch ROOM2.
        index.forget(7L, "tok-a", "ROOM1");
        assertEquals("ROOM2", index.roomForUser(7L));
        assertNull(index.roomForToken("tok-a"));
    }

    @Test
    void forget_tolerates_null_user_and_token() {
        ReconnectIndex index = new InMemoryReconnectIndex();
        index.register(null, "only-tok", "ROOM3");
        assertDoesNotThrow(() -> index.forget(null, "only-tok", "ROOM3"));
        assertNull(index.roomForToken("only-tok"));
    }
}
