package com.barbu.app.room;

import com.barbu.engine.match.MatchState;

/**
 * Durable state of a table, relocatable across pods. Contains <b>no</b> socket: the WS sessions are
 * pod-local and rebuilt on reconnect; the bots and the turn timer are recreated at rehydration from
 * {@code match}.
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
