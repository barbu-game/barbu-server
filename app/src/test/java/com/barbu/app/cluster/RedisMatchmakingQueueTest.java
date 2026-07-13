package com.barbu.app.cluster;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.cluster.MatchmakingQueue.Assignment;
import com.barbu.app.cluster.MatchmakingQueue.Entry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedisMatchmakingQueueTest {

    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static StatefulRedisConnection<String, String> conn;
    private RedisMatchmakingQueue queue;

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

    @BeforeEach
    void clean() {
        conn.sync().flushall();
        queue = new RedisMatchmakingQueue(conn, new ObjectMapper());
    }

    private Entry casual(String id, int size, long at) {
        return new Entry(id, null, "P", null, size, "pod-A", at);
    }

    @Test
    void casual_entries_round_trip_sorted_by_time() {
        queue.add(casual("b", 4, 200), 5_000);
        queue.add(casual("a", 4, 100), 5_000);
        assertEquals(
                List.of("a", "b"), queue.casual(4).stream().map(Entry::entryId).toList());
        assertTrue(queue.casual(3).isEmpty());
    }

    @Test
    void ranked_is_a_separate_queue() {
        queue.add(new Entry("r", 7L, "R", 1200, 4, "pod-A", 100), 5_000);
        queue.add(casual("c", 4, 100), 5_000);
        assertEquals(List.of("r"), queue.ranked().stream().map(Entry::entryId).toList());
        assertEquals(List.of("c"), queue.casual(4).stream().map(Entry::entryId).toList());
        assertEquals(2, queue.size());
    }

    @Test
    void remove_drops_entry_and_its_zset_member() {
        queue.add(casual("a", 4, 100), 5_000);
        queue.remove("a");
        assertTrue(queue.casual(4).isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void expired_entry_is_cleaned_lazily_on_read() throws Exception {
        queue.add(casual("a", 4, 100), 200); // TTL 200ms
        Thread.sleep(400);
        assertTrue(queue.casual(4).isEmpty(), "expired entry key gone → member cleaned on read");
        assertEquals(0, queue.size());
    }

    @Test
    void assignment_is_taken_once() {
        queue.assign("a", new Assignment("ROOM1", "tok", "pod-B"), 5_000);
        assertEquals("ROOM1", queue.takeAssignment("a").orElseThrow().roomId());
        assertTrue(queue.takeAssignment("a").isEmpty());
    }
}
