package com.barbu.app.room;

import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Localise la room d'un joueur pour la reconnexion : par compte ({@code userId}) et par resume
 * token (invités). En mémoire d'un seul pod ; les deux maps se transposent sur Redis en phase 6.
 */
@Singleton
public class ReconnectIndex {

    private final Map<Long, String> roomByUser = new ConcurrentHashMap<>();
    private final Map<String, String> roomByToken = new ConcurrentHashMap<>();

    /** À l'attribution d'un siège. {@code userId} null pour un invité ; {@code token} toujours présent. */
    public void register(Long userId, String token, String roomId) {
        if (userId != null) {
            roomByUser.put(userId, roomId);
        }
        if (token != null) {
            roomByToken.put(token, roomId);
        }
    }

    public String roomForUser(long userId) {
        return roomByUser.get(userId);
    }

    public String roomForToken(String token) {
        return token == null ? null : roomByToken.get(token);
    }

    /**
     * Oublie les entrées d'un siège quand sa room est détruite. Suppression <b>conditionnelle</b>
     * (clé, roomId) : si l'entrée pointe désormais sur une autre room (joueur parti ailleurs), on
     * ne l'efface pas.
     */
    public void forget(Long userId, String token, String roomId) {
        if (userId != null) {
            roomByUser.remove(userId, roomId);
        }
        if (token != null) {
            roomByToken.remove(token, roomId);
        }
    }
}
