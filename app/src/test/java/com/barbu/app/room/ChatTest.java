package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.protocol.ChatBroadcast;
import com.barbu.app.protocol.ChatFilter;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatTest {

    private final ChatFilter filter = new ChatFilter();

    @Test
    void produces_a_broadcast_for_a_human_message() {
        Optional<ChatBroadcast> out = GameRoom.prepareChat(1, "Alice", false, "bien joué", 1000L, 0L, filter);
        assertTrue(out.isPresent());
        assertEquals(1, out.get().seat());
        assertEquals("Alice", out.get().name());
        assertEquals("bien joué", out.get().text());
        assertEquals(1000L, out.get().ts());
    }

    @Test
    void rejects_a_bot_seat() {
        assertTrue(
                GameRoom.prepareChat(0, "Bot 0", true, "hi", 1000L, 0L, filter).isEmpty());
    }

    @Test
    void rejects_blank_text() {
        assertTrue(GameRoom.prepareChat(1, "Alice", false, "   ", 1000L, 0L, filter)
                .isEmpty());
    }

    @Test
    void rejects_a_message_inside_the_rate_limit_window() {
        // last message at 900ms, now 1000ms, interval 500ms → 100ms < 500ms → rejected
        assertTrue(GameRoom.prepareChat(1, "Alice", false, "spam", 1000L, 900L, filter)
                .isEmpty());
    }

    @Test
    void allows_a_message_after_the_rate_limit_window() {
        assertTrue(GameRoom.prepareChat(1, "Alice", false, "ok", 1500L, 900L, filter)
                .isPresent());
    }

    @Test
    void truncates_text_longer_than_the_limit() {
        String longText = "x".repeat(400);
        ChatBroadcast out = GameRoom.prepareChat(1, "Alice", false, longText, 1000L, 0L, filter)
                .orElseThrow();
        assertEquals(280, out.text().length());
    }

    @Test
    void masks_banned_words() {
        ChatBroadcast out = GameRoom.prepareChat(1, "Alice", false, "merde", 1000L, 0L, filter)
                .orElseThrow();
        assertEquals("****", out.text());
    }
}
