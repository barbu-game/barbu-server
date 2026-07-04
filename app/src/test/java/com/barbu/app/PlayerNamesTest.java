package com.barbu.app;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlayerNamesTest {

    @Test
    void rejects_blank_or_overlong_account_names() {
        assertFalse(PlayerNames.isValidAccountName(null));
        assertFalse(PlayerNames.isValidAccountName("   "));
        assertFalse(PlayerNames.isValidAccountName("a".repeat(PlayerNames.MAX_LEN + 1)));
        assertTrue(PlayerNames.isValidAccountName("alice"));
        assertTrue(PlayerNames.isValidAccountName("a".repeat(PlayerNames.MAX_LEN)));
        assertTrue(PlayerNames.isValidAccountName("  bob  "), "leading/trailing space is trimmed before measuring");
    }

    @Test
    void normalizes_and_clamps_guest_names() {
        assertNull(PlayerNames.normalizeGuest(null));
        assertNull(PlayerNames.normalizeGuest("   "));
        assertEquals("Pipou", PlayerNames.normalizeGuest("  Pipou  "));
        assertEquals("a".repeat(PlayerNames.MAX_LEN), PlayerNames.normalizeGuest("a".repeat(60)));
    }

    @Test
    void clamp_keeps_whole_code_points_at_the_boundary() {
        // 50 cat emojis = 50 code points but 100 UTF-16 units; clamp must not split a surrogate pair.
        String out = PlayerNames.normalizeGuest("🐱".repeat(50));
        assertEquals(PlayerNames.MAX_LEN, out.codePointCount(0, out.length()));
        assertEquals("🐱".repeat(PlayerNames.MAX_LEN), out);
    }
}
