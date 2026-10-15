package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.protocol.GameStateMessage;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** The rules of one game behind a {@link GameRoom}; the room owns seats, timers, votes and broadcast. */
interface GameSession {

    String gameType();

    boolean isStarted();

    void start(long seed);

    boolean isComplete();

    /** Seat expected to act now; empty while a step is held, between rounds, or once over. */
    OptionalInt currentActor();

    /** Applies a client move; an illegal or malformed one throws and leaves the state untouched. */
    void play(int seat, Map<String, Object> move);

    void playBot(int seat);

    /** Plays a whole turn on behalf of an idle human. */
    void playTimeout(int seat);

    /** Display time of a finished trick or round still on the table, cleared by {@link #runHoldStep}. */
    OptionalLong holdStepMs();

    void runHoldStep();

    boolean betweenRounds();

    void startNextRound();

    boolean atStopVoteBoundary();

    /** Seats from first to last place. */
    List<Integer> standings();

    void record(MatchRecorder recorder, String mode, List<MatchRecorder.PlayerInfo> players);

    GameStateMessage view(RoomContext ctx);

    GameSnapshot snapshot(
            String roomId,
            String mode,
            String[] names,
            boolean[] isBot,
            Long[] userIds,
            String[] resumeTokens,
            boolean stopped);
}
