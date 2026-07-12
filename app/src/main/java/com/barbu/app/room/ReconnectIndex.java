package com.barbu.app.room;

/**
 * Localise la room d'un joueur pour la reconnexion : par compte ({@code userId}) et par resume
 * token (invités). Impl mémoire (mono-pod) ou Redis (multi-pod), selon la présence de {@code redis.uri}.
 */
public interface ReconnectIndex {

    /** À l'attribution d'un siège. {@code userId} null pour un invité ; {@code token} toujours présent. */
    void register(Long userId, String token, String roomId);

    String roomForUser(long userId);

    String roomForToken(String token);

    /**
     * Oublie les entrées d'un siège quand sa room est détruite. Suppression <b>conditionnelle</b>
     * (clé, roomId) : si l'entrée pointe désormais sur une autre room (joueur parti ailleurs), on
     * ne l'efface pas.
     */
    void forget(Long userId, String token, String roomId);
}
