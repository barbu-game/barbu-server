package com.barbu.app.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisClusterStoreTest {

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
    void lease_is_exclusive_and_renewable() {
        RedisRoomRegistry reg = new RedisRoomRegistry(conn);
        assertTrue(reg.tryClaim("R1", "pod-0", 5_000));
        assertFalse(reg.tryClaim("R1", "pod-1", 5_000));
        assertTrue(reg.renew("R1", "pod-0", 5_000));
        assertFalse(reg.renew("R1", "pod-1", 5_000));
        reg.release("R1", "pod-0");
        assertTrue(reg.tryClaim("R1", "pod-1", 5_000));
    }

    @Test
    void snapshot_save_load_delete() {
        RedisSnapshotStore store = new RedisSnapshotStore(conn);
        store.save("R2", "{\"x\":1}");
        assertEquals("{\"x\":1}", store.load("R2").orElseThrow());
        store.delete("R2");
        assertTrue(store.load("R2").isEmpty());
    }
}
