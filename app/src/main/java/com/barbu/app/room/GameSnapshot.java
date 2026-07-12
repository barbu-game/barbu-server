package com.barbu.app.room;

import com.barbu.engine.match.MatchState;

/**
 * État durable d'une table, relocalisable entre pods. Ne contient <b>aucune</b> socket : les
 * sessions WS sont pod-locales et reconstruites au reconnect ; les bots et le timer de tour sont
 * recréés à la réhydratation à partir de {@code match}.
 */
public record GameSnapshot(
        String roomId,
        int playerCount,
        String variantId,
        String mode,
        String[] names,
        boolean[] isBot,
        Long[] userIds,
        String[] resumeTokens,
        boolean stopped,
        MatchState match) {}
