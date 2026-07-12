package com.barbu.app.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisReconnectIndexTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static StatefulRedisConnection<String, String> conn;

    @BeforeAll
    static void up() {
        redis.start();
        conn = RedisClient.create("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379))
                .connect();
    }

    @AfterAll
    static void down() {
        conn.close();
        redis.stop();
    }

    @Test
    void register_then_lookup_by_user_and_token() {
        RedisReconnectIndex index = new RedisReconnectIndex(conn);
        index.register(42L, "tok-1", "ROOMX");

        assertEquals("ROOMX", index.roomForUser(42L));
        assertEquals("ROOMX", index.roomForToken("tok-1"));

        index.forget(42L, "tok-1", "ROOMX");
        assertNull(index.roomForToken("tok-1"));
        assertNull(index.roomForUser(42L));
    }

    @Test
    void forget_is_conditional_on_room_id() {
        RedisReconnectIndex index = new RedisReconnectIndex(conn);
        index.register(5L, "tok-2", "ROOM-A");
        // The seat has since moved to another room; forgetting the old room must not clear it.
        index.register(5L, "tok-2", "ROOM-B");
        index.forget(5L, "tok-2", "ROOM-A");

        assertEquals("ROOM-B", index.roomForUser(5L));
        assertEquals("ROOM-B", index.roomForToken("tok-2"));
    }
}
