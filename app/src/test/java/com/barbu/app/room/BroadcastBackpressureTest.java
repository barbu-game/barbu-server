package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

class BroadcastBackpressureTest {

    /**
     * A back-pressured client (full TCP send buffer behind the Cloudflare tunnel) must not be able to
     * freeze the server: broadcasting state must never block the calling thread on a stuck write, or a
     * single slow client starves the event loop and the liveness probe restarts the pod mid-game.
     */
    @Test
    void broadcast_does_not_block_on_a_back_pressured_client() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch stuckUntilReleased = new CountDownLatch(1);
        try {
            GameRoom room = new GameRoom("BP1", 2, Variants.DEVELOPER, new ObjectMapper(), scheduler, 0, null, null);
            room.addHuman(
                    new FakeSession() {
                        @Override
                        public void sendSync(Object message) {
                            try {
                                stuckUntilReleased.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    },
                    "stuck",
                    null);

            Thread caller = new Thread(room::broadcast, "broadcast-caller");
            caller.setDaemon(true);
            caller.start();
            caller.join(1000);

            assertFalse(caller.isAlive(), "broadcast must not block the caller on a stuck/slow client");
        } finally {
            stuckUntilReleased.countDown();
            scheduler.shutdownNow();
        }
    }
}
