package com.barbu.app.cluster;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.cluster.MatchmakingQueue.Assignment;
import com.barbu.app.cluster.MatchmakingQueue.Entry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryMatchmakingQueueTest {

    private final AtomicLong now = new AtomicLong(1_000);

    private InMemoryMatchmakingQueue queue() {
        return new InMemoryMatchmakingQueue(now::get);
    }

    private Entry casual(String id, int size, long at) {
        return new Entry(id, null, "P", null, size, "pod-A", at);
    }

    @Test
    void casual_entries_come_back_sorted_by_enqueue_time() {
        InMemoryMatchmakingQueue q = queue();
        q.add(casual("b", 4, 200), 5_000);
        q.add(casual("a", 4, 100), 5_000);
        assertEquals(List.of("a", "b"), q.casual(4).stream().map(Entry::entryId).toList());
        assertTrue(q.casual(3).isEmpty(), "another size is a separate queue");
        assertEquals(2, q.size());
    }

    @Test
    void ranked_and_casual_are_separate_queues() {
        InMemoryMatchmakingQueue q = queue();
        q.add(new Entry("r", 7L, "R", 1200, 4, "pod-A", 100), 5_000);
        q.add(casual("c", 4, 100), 5_000);
        assertEquals(List.of("r"), q.ranked().stream().map(Entry::entryId).toList());
        assertEquals(List.of("c"), q.casual(4).stream().map(Entry::entryId).toList());
    }

    @Test
    void remove_drops_the_entry() {
        InMemoryMatchmakingQueue q = queue();
        q.add(casual("a", 4, 100), 5_000);
        q.remove("a");
        assertTrue(q.casual(4).isEmpty());
        assertEquals(0, q.size());
    }

    @Test
    void an_entry_expires_when_its_ttl_lapses_without_renew() {
        InMemoryMatchmakingQueue q = queue();
        q.add(casual("a", 4, 100), 5_000);
        now.addAndGet(4_000);
        q.renew("a", 5_000); // repousse l'expiration à now+5000
        now.addAndGet(4_000); // 4s après le renew → encore vivant
        assertEquals(1, q.casual(4).size());
        now.addAndGet(2_000); // 6s après le renew → expiré
        assertTrue(q.casual(4).isEmpty());
    }

    @Test
    void assignment_is_read_once_then_gone() {
        InMemoryMatchmakingQueue q = queue();
        q.assign("a", new Assignment("ROOM1", "tok", "pod-B"), 5_000);
        assertEquals("ROOM1", q.takeAssignment("a").orElseThrow().roomId());
        assertTrue(q.takeAssignment("a").isEmpty(), "an assignment is consumed exactly once");
    }
}
