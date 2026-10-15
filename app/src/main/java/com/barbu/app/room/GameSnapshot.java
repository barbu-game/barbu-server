package com.barbu.app.room;

import com.barbu.engine.match.MatchState;
import com.barbu.rami.match.RamiMatch;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Durable state of a table, relocatable between pods. Holds <b>no</b> socket: WS sessions are
 * pod-local and rebuilt on reconnect; bots and the turn timer are recreated on rehydration. {@code
 * match} carries Barbu state, {@code rami} Rami state; a missing {@code gameType} (older snapshot)
 * means Barbu.
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
        MatchState match,
        String gameType,
        RamiMatch rami) {

    @JsonCreator
    public GameSnapshot {}

    public GameSnapshot(
            String roomId,
            int playerCount,
            String variantId,
            String mode,
            String[] names,
            boolean[] isBot,
            Long[] userIds,
            String[] resumeTokens,
            boolean stopped,
            MatchState match) {
        this(roomId, playerCount, variantId, mode, names, isBot, userIds, resumeTokens, stopped, match, "BARBU", null);
    }
}
