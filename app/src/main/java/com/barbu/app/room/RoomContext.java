package com.barbu.app.room;

import com.barbu.app.protocol.GameStateMessage.PauseVoteState;
import com.barbu.app.protocol.GameStateMessage.PausedState;
import com.barbu.app.protocol.GameStateMessage.PlayerInfo;
import com.barbu.app.protocol.GameStateMessage.VoteState;
import java.util.List;

/** Room-owned parts of a seat's view, handed to the session that renders the game part. */
record RoomContext(
        String roomId,
        int playerCount,
        int seat,
        String resumeToken,
        String phase,
        List<PlayerInfo> players,
        String[] names,
        boolean finished,
        VoteState stopVote,
        PauseVoteState pauseVote,
        PausedState paused,
        Long turnDeadline) {}
