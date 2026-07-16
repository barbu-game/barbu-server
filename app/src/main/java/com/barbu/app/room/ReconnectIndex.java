package com.barbu.app.room;

/**
 * Locates a player's room for reconnection: by account ({@code userId}) and by resume token
 * (guests). In-memory impl (single-pod) or Redis (multi-pod), depending on the presence of {@code redis.uri}.
 */
public interface ReconnectIndex {

    /** On seat assignment. {@code userId} null for a guest; {@code token} always present. */
    void register(Long userId, String token, String roomId);

    String roomForUser(long userId);

    String roomForToken(String token);

    /**
     * Forgets a seat's entries when its room is destroyed. <b>Conditional</b> removal
     * (key, roomId): if the entry now points to another room (player moved elsewhere), it is
     * not erased.
     */
    void forget(Long userId, String token, String roomId);
}
