package com.barbu.app.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryRoomRegistryTest {

    @Test
    void claim_is_exclusive_until_released() {
        InMemoryRoomRegistry reg = new InMemoryRoomRegistry();
        assertTrue(reg.tryClaim("R1", "pod-0", 10_000));
        assertFalse(reg.tryClaim("R1", "pod-1", 10_000));
        assertEquals("pod-0", reg.ownerOf("R1").orElseThrow());

        reg.release("R1", "pod-0");
        assertTrue(reg.tryClaim("R1", "pod-1", 10_000));
    }

    @Test
    void expired_lease_becomes_claimable() throws InterruptedException {
        InMemoryRoomRegistry reg = new InMemoryRoomRegistry();
        assertTrue(reg.tryClaim("R2", "pod-0", 50));
        Thread.sleep(80);
        assertTrue(reg.tryClaim("R2", "pod-1", 10_000));
    }

    @Test
    void renew_only_by_owner() {
        InMemoryRoomRegistry reg = new InMemoryRoomRegistry();
        reg.tryClaim("R3", "pod-0", 10_000);
        assertTrue(reg.renew("R3", "pod-0", 10_000));
        assertFalse(reg.renew("R3", "pod-1", 10_000));
    }
}
