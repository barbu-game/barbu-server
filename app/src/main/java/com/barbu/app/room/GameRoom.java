package com.barbu.app.room;

import com.barbu.app.PlayerNames;
import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.protocol.ChatBroadcast;
import com.barbu.app.protocol.ChatFilter;
import com.barbu.app.protocol.GameStateMessage;
import com.barbu.app.protocol.GameStateMessage.PauseVoteState;
import com.barbu.app.protocol.GameStateMessage.PausedState;
import com.barbu.app.protocol.GameStateMessage.PlayerInfo;
import com.barbu.app.protocol.GameStateMessage.VoteState;
import com.barbu.app.rating.RatingService;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.websocket.WebSocketSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One game table held in a single pod's memory. All mutations are serialized on the
 * room monitor; bot turns are paced through a scheduler so a human watches them unfold.
 */
public final class GameRoom {

    private final String id;
    private final int playerCount;
    private final GameSession game;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final long botDelayMs;
    private final MatchRecorder recorder;
    private final com.barbu.app.metrics.GameMetrics metrics;
    /** Private table whose players opted out of the turn timer; the mode is what persists it. */
    public static final String UNTIMED = "untimed";

    private final String mode;
    private final RatingService ratingService;

    private static final Logger LOG = LoggerFactory.getLogger(GameRoom.class);

    private static final long VOTE_TIMEOUT_MS = 15000;

    private static final long PAUSE_VOTE_TIMEOUT_MS = 15000;
    private static final long PAUSE_DURATION_MS = 60000;

    private static final int MAX_CHAT_LEN = 280;
    private static final long CHAT_MIN_INTERVAL_MS = 500;

    private final String[] names;
    private final boolean[] isBot;
    private final Long[] userIds;
    private final WebSocketSession[] sessions;
    private final long[] lastChatAt;
    private final String[] resumeTokens;
    private final ReconnectIndex reconnectIndex;
    private final ChatFilter chatFilter = new ChatFilter();

    private boolean recorded;
    private boolean stopped;
    private boolean stepPending;
    private boolean voteOpen;
    private Boolean[] votes;
    private ScheduledFuture<?> voteTimeout;
    private boolean pauseVoteOpen;
    private Boolean[] pauseVotes;
    private ScheduledFuture<?> pauseVoteTimeout;
    private boolean paused;
    private long pauseEndsAtMs;
    private ScheduledFuture<?> pauseEnd;

    private ScheduledFuture<?> pendingTeardown;

    private ScheduledFuture<?> turnTimeout;
    private int turnTimeoutSeat = -1;
    private long turnDeadlineEpochMs;
    private final long turnTimeoutMs;
    private final int turnTimeoutStrikes;
    private final int[] strikes;
    private final boolean[] abandoned;

    GameRoom(
            String id,
            int playerCount,
            Variant variant,
            ObjectMapper mapper,
            ScheduledExecutorService scheduler,
            long botDelayMs,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics) {
        this(
                id,
                playerCount,
                variant,
                mapper,
                scheduler,
                botDelayMs,
                recorder,
                metrics,
                "private",
                null,
                null,
                60000,
                2);
    }

    GameRoom(
            String id,
            int playerCount,
            Variant variant,
            ObjectMapper mapper,
            ScheduledExecutorService scheduler,
            long botDelayMs,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics,
            String mode,
            RatingService ratingService,
            ReconnectIndex reconnectIndex,
            long turnTimeoutMs,
            int turnTimeoutStrikes) {
        this(
                id,
                playerCount,
                new BarbuSession(playerCount, variant, null),
                mapper,
                scheduler,
                botDelayMs,
                recorder,
                metrics,
                mode,
                ratingService,
                reconnectIndex,
                turnTimeoutMs,
                turnTimeoutStrikes);
    }

    GameRoom(
            String id,
            int playerCount,
            GameSession game,
            ObjectMapper mapper,
            ScheduledExecutorService scheduler,
            long botDelayMs,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics,
            String mode,
            RatingService ratingService,
            ReconnectIndex reconnectIndex,
            long turnTimeoutMs,
            int turnTimeoutStrikes) {
        this.id = id;
        this.playerCount = playerCount;
        this.game = game;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.botDelayMs = botDelayMs;
        this.recorder = recorder;
        this.metrics = metrics;
        this.mode = mode;
        this.ratingService = ratingService;
        this.reconnectIndex = reconnectIndex;
        this.turnTimeoutMs = turnTimeoutMs;
        this.turnTimeoutStrikes = turnTimeoutStrikes;
        this.names = new String[playerCount];
        this.isBot = new boolean[playerCount];
        this.userIds = new Long[playerCount];
        this.sessions = new WebSocketSession[playerCount];
        this.lastChatAt = new long[playerCount];
        this.resumeTokens = new String[playerCount];
        this.strikes = new int[playerCount];
        this.abandoned = new boolean[playerCount];
    }

    public String id() {
        return id;
    }

    private Runnable onStateChanged = () -> {};

    /** Persistence hook called after every state change (wired by the {@link RoomManager}). */
    public synchronized void setOnStateChanged(Runnable hook) {
        this.onStateChanged = hook == null ? () -> {} : hook;
    }

    public synchronized GameSnapshot snapshot() {
        return game.snapshot(id, mode, names.clone(), isBot.clone(), userIds.clone(), resumeTokens.clone(), stopped);
    }

    /**
     * Rebuilds a room from a durable snapshot: seats and engine state restored, WS sessions null
     * (rebuilt on reclaim), bots and turn timer recreated on the first {@code resume()} triggered by
     * a reconnection.
     */
    public static GameRoom fromSnapshot(
            GameSnapshot snap,
            ObjectMapper mapper,
            ScheduledExecutorService scheduler,
            long botDelayMs,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics,
            RatingService ratingService,
            ReconnectIndex reconnectIndex,
            long turnTimeoutMs,
            int turnTimeoutStrikes) {
        GameRoom room = new GameRoom(
                snap.roomId(),
                snap.playerCount(),
                sessionFor(snap),
                mapper,
                scheduler,
                botDelayMs,
                recorder,
                metrics,
                snap.mode(),
                ratingService,
                reconnectIndex,
                turnTimeoutMs,
                turnTimeoutStrikes);
        room.restoreState(snap);
        return room;
    }

    private static GameSession sessionFor(GameSnapshot snap) {
        if ("RAMI".equals(snap.gameType())) {
            return new RamiSession(snap.playerCount(), snap.rami());
        }
        return new BarbuSession(snap.playerCount(), Variants.byId(snap.variantId()), snap.match());
    }

    private synchronized void restoreState(GameSnapshot snap) {
        System.arraycopy(snap.names(), 0, names, 0, playerCount);
        System.arraycopy(snap.isBot(), 0, isBot, 0, playerCount);
        System.arraycopy(snap.userIds(), 0, userIds, 0, playerCount);
        System.arraycopy(snap.resumeTokens(), 0, resumeTokens, 0, playerCount);
        this.stopped = snap.stopped();
        if (reconnectIndex != null) {
            for (int s = 0; s < playerCount; s++) {
                reconnectIndex.register(userIds[s], resumeTokens[s], id);
            }
        }
    }

    public synchronized int connectedHumanCount() {
        int n = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && sessions[seat] != null) {
                n++;
            }
        }
        return n;
    }

    public synchronized int botCount() {
        int n = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            if (isBot[seat]) {
                n++;
            }
        }
        return n;
    }

    public synchronized int addHuman(WebSocketSession session, String name, Long userId) {
        cancelPendingTeardown();
        int seat = firstFreeSeat();
        if (seat < 0) {
            return -1;
        }
        sessions[seat] = session;
        String normalized = PlayerNames.normalizeGuest(name);
        names[seat] = normalized == null ? "Player " + seat : normalized;
        userIds[seat] = userId;
        isBot[seat] = false;
        resumeTokens[seat] = UUID.randomUUID().toString();
        if (reconnectIndex != null) {
            reconnectIndex.register(userId, resumeTokens[seat], id);
        }
        return seat;
    }

    /**
     * Reserves the next free seat for a player not yet connected to this pod: fresh resume token +
     * index entry, but {@code sessions[seat] == null}. The player takes the seat on {@code reclaim}
     * (via {@code resume}); in the meantime, the state is that of a disconnection at t=0 (substitute
     * bot after strikes). Used by matchmaking to seat a player from another pod. Returns the resume
     * token, or {@code null} if the table is full.
     */
    public synchronized String reserveHuman(String name, Long userId) {
        cancelPendingTeardown();
        int seat = firstFreeSeat();
        if (seat < 0) {
            return null;
        }
        sessions[seat] = null;
        String normalized = PlayerNames.normalizeGuest(name);
        names[seat] = normalized == null ? "Player " + seat : normalized;
        userIds[seat] = userId;
        isBot[seat] = false;
        resumeTokens[seat] = UUID.randomUUID().toString();
        if (reconnectIndex != null) {
            reconnectIndex.register(userId, resumeTokens[seat], id);
        }
        return resumeTokens[seat];
    }

    public synchronized boolean addBot() {
        if (game.isStarted()) {
            return false;
        }
        int seat = firstFreeSeat();
        if (seat < 0) {
            return false;
        }
        names[seat] = "Bot " + (botCount() + 1);
        isBot[seat] = true;
        return true;
    }

    /** Host action (gating done in the WS handler): rename a lobby bot. Blank name → no change. */
    public synchronized boolean renameBot(int seat, String name) {
        if (game.isStarted() || seat < 0 || seat >= playerCount || !isBot[seat]) {
            return false;
        }
        String normalized = PlayerNames.normalizeGuest(name);
        if (normalized == null) {
            return false;
        }
        names[seat] = normalized;
        return true;
    }

    public synchronized boolean start(long seed) {
        if (game.isStarted() || !isFull()) {
            return false;
        }
        game.start(seed);
        if (metrics != null) {
            metrics.gameStarted();
        }
        broadcast();
        resume();
        return true;
    }

    /** Applies a seated human's move; an illegal or malformed one throws and changes nothing. */
    public synchronized void play(int seat, Map<String, Object> move) {
        if (voteOpen || stepPending || isBot[seat] || game.currentActor().orElse(-1) != seat) {
            return;
        }
        game.play(seat, move);
        if (game.currentActor().orElse(-1) == seat) {
            // Same turn goes on (Rami sub-moves): the deadline covers the whole turn, not each move.
            broadcast();
            return;
        }
        strikes[seat] = 0;
        cancelTurnTimer();
        afterAdvance();
    }

    /** A human votes at a dealer boundary: true = stop the game here, false = keep playing. */
    public synchronized void castStopVote(int seat, boolean stop) {
        if (!voteOpen || seat < 0 || seat >= playerCount || isBot[seat]) {
            return;
        }
        votes[seat] = stop;
        if (allHumansVoted(votes)) {
            resolveVote();
        } else {
            broadcast();
        }
    }

    /**
     * A human types {@code /pause}. The first valid call opens the vote (at a between-rounds
     * boundary only); the following ones record the votes. Strict majority required.
     */
    public synchronized void castPauseVote(int seat, boolean pause) {
        if (seat < 0 || seat >= playerCount || isBot[seat]) {
            return;
        }
        if (!pauseVoteOpen) {
            if (!pauseEligible()) {
                return;
            }
            openPauseVote(seat);
        }
        pauseVotes[seat] = pause;
        if (allHumansVoted(pauseVotes)) {
            resolvePauseVote();
        } else {
            broadcast();
        }
    }

    /** A human types {@code /resume}: early resume if a pause is active. */
    public synchronized void resumeGame(int seat) {
        if (!paused || seat < 0 || seat >= playerCount || isBot[seat]) {
            return;
        }
        broadcastSystemChat(seat, names[seat] + " relance la partie");
        endPause();
    }

    /** A seated human writes in the table chat. Broadcast to everyone, never persisted. */
    public synchronized void chat(int seat, String rawText) {
        if (seat < 0 || seat >= playerCount) {
            return;
        }
        long now = System.currentTimeMillis();
        Optional<ChatBroadcast> message =
                prepareChat(seat, names[seat], isBot[seat], rawText, now, lastChatAt[seat], chatFilter);
        if (message.isEmpty()) {
            return;
        }
        lastChatAt[seat] = now;
        ChatBroadcast m = message.get();
        broadcastRaw(Map.of(
                "type", "chat",
                "seat", m.seat(),
                "name", m.name(),
                "text", m.text(),
                "ts", m.ts(),
                "system", m.system()));
    }

    static boolean stopVotePasses(int stopVotes, int humans) {
        return stopVotes * 2 > humans;
    }

    /**
     * Pure eligibility of a pause vote, without room state: allowed only at a between-rounds
     * boundary (never mid-trick), if no other vote/pause is in progress and at least one human is
     * seated. {@code matchActive} = game started and not finished;
     * {@code betweenRounds} = {@code match.round() == null}.
     */
    static boolean pauseAllowed(
            boolean stopped,
            boolean stopVoteOpen,
            boolean pauseVoteOpen,
            boolean paused,
            boolean matchActive,
            boolean betweenRounds,
            int humans) {
        return !stopped && !stopVoteOpen && !pauseVoteOpen && !paused && matchActive && betweenRounds && humans > 0;
    }

    /** ELO chat line: {@code "Alice : 1200 → 1215 (+15)"} (explicit sign, +0 for a draw). */
    static String eloChatLine(String name, int before, int after, int delta) {
        String sign = delta >= 0 ? "+" : "";
        return name + " : " + before + " → " + after + " (" + sign + delta + ")";
    }

    /**
     * Index of the disconnected seat this returning player can reclaim, or -1. The token (guest
     * capability) takes precedence over the userId. {@code occupied[s]} = a session is already active on the seat.
     */
    static int reclaimableSeat(Long userId, String token, Long[] userIds, String[] tokens, boolean[] occupied) {
        if (token != null) {
            for (int s = 0; s < tokens.length; s++) {
                if (!occupied[s] && token.equals(tokens[s])) {
                    return s;
                }
            }
        }
        if (userId != null) {
            for (int s = 0; s < userIds.length; s++) {
                if (!occupied[s] && userId.equals(userIds[s])) {
                    return s;
                }
            }
        }
        return -1;
    }

    /**
     * Decides a chat message without any side effect: returns the {@link ChatBroadcast} to
     * broadcast, or empty if the message is ignored (bot seat, empty text, or anti-spam).
     */
    static Optional<ChatBroadcast> prepareChat(
            int seat, String name, boolean isBot, String rawText, long now, long lastChatAt, ChatFilter filter) {
        if (isBot || rawText == null) {
            return Optional.empty();
        }
        String trimmed = rawText.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (now - lastChatAt < CHAT_MIN_INTERVAL_MS) {
            return Optional.empty();
        }
        if (trimmed.length() > MAX_CHAT_LEN) {
            trimmed = trimmed.substring(0, MAX_CHAT_LEN);
        }
        String clean = filter.sanitize(trimmed);
        return Optional.of(new ChatBroadcast(seat, name, clean, now, false));
    }

    /**
     * A human left: the seat stays human but flagged disconnected. The turn timeout drives play and
     * hands the seat to a bot only after the configured strikes, so a quick reconnect can reclaim it.
     */
    public synchronized void handleDisconnect(int seat) {
        if (seat < 0 || seat >= playerCount) {
            return;
        }
        sessions[seat] = null;
        if (game.isStarted() && !isBot[seat]) {
            broadcastSystemChat(seat, names[seat] + " s'est déconnecté");
        }
        // Always rebroadcast so lobby peers see the seat flip to disconnected; the seat stays reserved.
        broadcast();
    }

    /**
     * A returning player reclaims their seat: rebinds the session, lifts the bot, broadcasts. Returns
     * the reclaimed seat, or -1 if no disconnected seat belongs to them.
     */
    public synchronized int reclaim(WebSocketSession session, Long userId, String token) {
        boolean[] occupied = new boolean[playerCount];
        for (int s = 0; s < playerCount; s++) {
            occupied[s] = sessions[s] != null;
        }
        int seat = reclaimableSeat(userId, token, userIds, resumeTokens, occupied);
        if (seat < 0) {
            return -1;
        }
        cancelPendingTeardown();
        sessions[seat] = session;
        isBot[seat] = false;
        abandoned[seat] = false;
        strikes[seat] = 0;
        broadcastSystemChat(seat, names[seat] + " est revenu à la table");
        broadcast();
        return seat;
    }

    /** Purges the index entries of all seats (called on room destruction). */
    public synchronized void clearReconnectEntries(ReconnectIndex index) {
        if (index == null) {
            return;
        }
        for (int s = 0; s < playerCount; s++) {
            index.forget(userIds[s], resumeTokens[s], id);
        }
    }

    public synchronized boolean isEmptyOfHumans() {
        for (WebSocketSession s : sessions) {
            if (s != null) {
                return false;
            }
        }
        return true;
    }

    /** Host = lowest-seat connected human; -1 if none. Drives client-command gating and migration. */
    public synchronized int hostSeat() {
        for (int s = 0; s < playerCount; s++) {
            if (!isBot[s] && sessions[s] != null) {
                return s;
            }
        }
        return -1;
    }

    public synchronized boolean isHost(int seat) {
        return seat >= 0 && seat == hostSeat();
    }

    /**
     * The last human dropped: defer teardown by a grace window instead of destroying the room at once,
     * so a backgrounded host (or an invited joiner) can come back to the same code. The room and its
     * reconnect entries survive the window; teardown only runs if nobody has returned by then.
     */
    public synchronized void scheduleTeardown(long graceMs, Runnable teardown) {
        cancelPendingTeardown();
        if (scheduler == null) {
            return;
        }
        pendingTeardown = scheduler.schedule(() -> runTeardown(teardown), graceMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void runTeardown(Runnable teardown) {
        pendingTeardown = null;
        if (isEmptyOfHumans()) {
            teardown.run();
        }
    }

    public synchronized void cancelPendingTeardown() {
        if (pendingTeardown != null) {
            pendingTeardown.cancel(false);
            pendingTeardown = null;
        }
    }

    /** A human intentionally leaves the lobby: the seat is fully freed for reuse. Lobby-only. */
    public synchronized boolean leave(int seat) {
        if (game.isStarted() || seat < 0 || seat >= playerCount || sessions[seat] == null) {
            return false;
        }
        if (reconnectIndex != null) {
            reconnectIndex.forget(userIds[seat], resumeTokens[seat], id);
        }
        sessions[seat] = null;
        isBot[seat] = false;
        names[seat] = "Player " + seat;
        userIds[seat] = null;
        resumeTokens[seat] = null;
        return true;
    }

    private boolean currentActorIsBot() {
        if (stopped || voteOpen || stepPending) {
            return false;
        }
        OptionalInt actor = game.currentActor();
        return actor.isPresent() && isBot[actor.getAsInt()];
    }

    /** Drive the table forward: collect a held step, deal the next round, or let a waiting bot act. */
    private void resume() {
        if (stopped || voteOpen || pauseVoteOpen || paused || !game.isStarted() || game.isComplete()) {
            return;
        }
        if (game.holdStepMs().isPresent()) {
            scheduleHoldStep();
        } else if (game.betweenRounds()) {
            // Pause on an empty table so the round-switch recap can be shown before the next deal.
            scheduler.schedule(this::startRoundStep, roundSwitchPauseMs(), TimeUnit.MILLISECONDS);
        } else {
            scheduleActor();
        }
    }

    private synchronized void startRoundStep() {
        if (stopped || voteOpen || pauseVoteOpen || paused || !game.betweenRounds()) {
            return;
        }
        game.startNextRound();
        broadcast();
        scheduleActor();
    }

    private void afterAdvance() {
        broadcast();
        if (game.holdStepMs().isPresent()) {
            // Hold the finished trick or round on the table; nobody acts until it is collected.
            scheduleHoldStep();
        } else if (game.betweenRounds() && isVotableBoundary()) {
            openVote();
        } else {
            resume();
        }
    }

    private void scheduleHoldStep() {
        if (stepPending) {
            return;
        }
        stepPending = true;
        scheduler.schedule(this::holdStep, scaled(game.holdStepMs().getAsLong()), TimeUnit.MILLISECONDS);
    }

    private synchronized void holdStep() {
        if (!stepPending) {
            return;
        }
        stepPending = false;
        game.runHoldStep();
        afterAdvance();
    }

    private long scaled(long pauseMs) {
        return botDelayMs == 0 ? 0 : pauseMs;
    }

    private long roundSwitchPauseMs() {
        return scaled(3500);
    }

    private boolean isVotableBoundary() {
        return !voteOpen && !stopped && game.atStopVoteBoundary() && humanSeatCount() > 0;
    }

    private int humanSeatCount() {
        int count = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat]) {
                count++;
            }
        }
        return count;
    }

    private void openVote() {
        voteOpen = true;
        votes = new Boolean[playerCount];
        broadcast();
        voteTimeout = scheduler.schedule(this::onVoteTimeout, VOTE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void onVoteTimeout() {
        if (voteOpen) {
            resolveVote();
        }
    }

    /** Have all human seats cast a vote in the given array? */
    private boolean allHumansVoted(Boolean[] ballots) {
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && ballots[seat] == null) {
                return false;
            }
        }
        return true;
    }

    /** Number of "yes" (true) votes cast by humans in the given array. */
    private int yesVotes(Boolean[] ballots) {
        int yes = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && Boolean.TRUE.equals(ballots[seat])) {
                yes++;
            }
        }
        return yes;
    }

    private void resolveVote() {
        voteOpen = false;
        if (voteTimeout != null) {
            voteTimeout.cancel(false);
            voteTimeout = null;
        }
        if (stopVotePasses(yesVotes(votes), humanSeatCount())) {
            stopped = true;
            broadcast();
        } else {
            broadcast();
            resume();
        }
    }

    private boolean pauseEligible() {
        return pauseAllowed(
                stopped,
                voteOpen,
                pauseVoteOpen,
                paused,
                game.isStarted() && !game.isComplete(),
                game.betweenRounds(),
                humanSeatCount());
    }

    private void openPauseVote(int starter) {
        pauseVoteOpen = true;
        pauseVotes = new Boolean[playerCount];
        broadcastSystemChat(starter, names[starter] + " demande une pause d'une minute (votez)");
        broadcast();
        pauseVoteTimeout = scheduler.schedule(this::onPauseVoteTimeout, PAUSE_VOTE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void onPauseVoteTimeout() {
        if (pauseVoteOpen) {
            resolvePauseVote();
        }
    }

    private void resolvePauseVote() {
        pauseVoteOpen = false;
        if (pauseVoteTimeout != null) {
            pauseVoteTimeout.cancel(false);
            pauseVoteTimeout = null;
        }
        if (stopVotePasses(yesVotes(pauseVotes), humanSeatCount())) {
            paused = true;
            pauseEndsAtMs = System.currentTimeMillis() + PAUSE_DURATION_MS;
            broadcast();
            pauseEnd = scheduler.schedule(this::onPauseTimeout, PAUSE_DURATION_MS, TimeUnit.MILLISECONDS);
        } else {
            broadcast();
            resume();
        }
    }

    private synchronized void onPauseTimeout() {
        endPause();
    }

    private void endPause() {
        if (!paused) {
            return;
        }
        paused = false;
        pauseEndsAtMs = 0;
        if (pauseEnd != null) {
            pauseEnd.cancel(false);
            pauseEnd = null;
        }
        broadcast();
        resume();
    }

    /** System chat line (deco/reco/bot/ELO/pause): attributed to a seat, rendered separately on the client. */
    private void broadcastSystemChat(int seat, String text) {
        broadcastRaw(Map.of(
                "type",
                "chat",
                "seat",
                seat,
                "name",
                names[seat],
                "text",
                text,
                "ts",
                System.currentTimeMillis(),
                "system",
                true));
    }

    /** Hand control to whoever must act next: a bot move, or a human turn under timeout. */
    private void scheduleActor() {
        cancelTurnTimer();
        if (currentActorIsBot()) {
            scheduler.schedule(this::botStep, botDelayMs, TimeUnit.MILLISECONDS);
        } else {
            armTurnTimer();
        }
    }

    private void armTurnTimer() {
        if (stopped || voteOpen || stepPending || UNTIMED.equals(mode)) {
            return;
        }
        OptionalInt actor = game.currentActor();
        if (actor.isEmpty() || isBot[actor.getAsInt()]) {
            return;
        }
        int seat = actor.getAsInt();
        turnTimeoutSeat = seat;
        turnDeadlineEpochMs = System.currentTimeMillis() + turnTimeoutMs;
        turnTimeout = scheduler.schedule(() -> onTurnTimeout(seat), turnTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void onTurnTimeout(int seat) {
        if (stopped || voteOpen || stepPending || game.currentActor().orElse(-1) != seat || isBot[seat]) {
            return;
        }
        game.playTimeout(seat);
        if (++strikes[seat] >= turnTimeoutStrikes) {
            isBot[seat] = true;
            abandoned[seat] = true;
            broadcastSystemChat(seat, names[seat] + " laisse sa place à un bot");
        }
        afterAdvance();
    }

    private synchronized void botStep() {
        if (!currentActorIsBot()) {
            return;
        }
        game.playBot(game.currentActor().getAsInt());
        afterAdvance();
    }

    private void cancelTurnTimer() {
        if (turnTimeout != null) {
            turnTimeout.cancel(false);
            turnTimeout = null;
        }
        turnTimeoutSeat = -1;
        turnDeadlineEpochMs = 0;
    }

    // A seat is open for a new arrival only if it was never claimed. A disconnected human keeps a
    // resume token, which reserves the seat for their reclaim instead of handing it to a joiner.
    private boolean seatIsOpen(int seat) {
        return !isBot[seat] && sessions[seat] == null && resumeTokens[seat] == null;
    }

    private int firstFreeSeat() {
        for (int seat = 0; seat < playerCount; seat++) {
            if (seatIsOpen(seat)) {
                return seat;
            }
        }
        return -1;
    }

    private boolean isFull() {
        for (int seat = 0; seat < playerCount; seat++) {
            if (seatIsOpen(seat)) {
                return false;
            }
        }
        return true;
    }

    public synchronized void broadcast() {
        for (int seat = 0; seat < playerCount; seat++) {
            WebSocketSession session = sessions[seat];
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                // sendAsync, never sendSync: a back-pressured client must not block this thread (the
                // event loop or the shared scheduler) or it starves /health and the pod gets restarted.
                session.sendAsync(mapper.writeValueAsString(viewFor(seat)));
            } catch (Exception ignored) {
                // a dropped client is reconciled on its next connect via a fresh snapshot
            }
        }
        maybeRecord();
        onStateChanged.run();
    }

    /** Sends the same payload to all open sessions (no per-seat redaction). */
    private synchronized void broadcastRaw(Map<String, Object> payload) {
        for (int seat = 0; seat < playerCount; seat++) {
            WebSocketSession session = sessions[seat];
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendAsync(mapper.writeValueAsString(payload));
            } catch (Exception ignored) {
                // a dropped client is reconciled on its next snapshot; chat is best-effort
            }
        }
    }

    private void maybeRecord() {
        if (recorder == null || recorded || !game.isStarted() || !(stopped || game.isComplete())) {
            return;
        }
        recorded = true;
        if (metrics != null) {
            metrics.gameFinished();
        }
        List<MatchRecorder.PlayerInfo> players = new ArrayList<>(playerCount);
        for (int seat = 0; seat < playerCount; seat++) {
            players.add(new MatchRecorder.PlayerInfo(seat, names[seat], isBot[seat], userIds[seat]));
        }
        try {
            game.record(recorder, mode, players);
        } catch (Exception e) {
            // persistence is best-effort; a failure must not interrupt play
            LOG.warn("match recording failed for room {} (best-effort, game unaffected)", id, e);
        }

        if (ratingService != null && "ranked".equals(mode) && game.isComplete()) {
            try {
                broadcastRankedResult(ratingService.applyRankedResult(game.gameType(), eloSeats()));
            } catch (Exception e) {
                // ELO is best-effort: a failure must not interrupt the end of the game
                LOG.warn("ranked ELO update failed for room {} (best-effort, game unaffected)", id, e);
            }
        }
    }

    /**
     * Places each non-abandoned seat by standings order (score), then puts all abandoned seats at
     * the same last place tied — "a leaver has necessarily lost".
     */
    static int[] placementsForElo(List<Integer> standingsOrder, boolean[] abandonedForElo) {
        int[] placement = new int[abandonedForElo.length];
        int pos = 1;
        for (int seat : standingsOrder) {
            if (!abandonedForElo[seat]) {
                placement[seat] = pos++;
            }
        }
        for (int seat = 0; seat < abandonedForElo.length; seat++) {
            if (abandonedForElo[seat]) {
                placement[seat] = pos;
            }
        }
        return placement;
    }

    /** A human account excluded (strikes) or disconnected is treated as a leaver for ELO. */
    private boolean[] abandonedForElo() {
        boolean[] flags = new boolean[playerCount];
        for (int seat = 0; seat < playerCount; seat++) {
            flags[seat] = userIds[seat] != null && (abandoned[seat] || sessions[seat] == null);
        }
        return flags;
    }

    private List<RatingService.SeatRating> eloSeats() {
        boolean[] abandonedForElo = abandonedForElo();
        int[] placement = placementsForElo(game.standings(), abandonedForElo);
        List<RatingService.SeatRating> seats = new ArrayList<>(playerCount);
        for (int seat = 0; seat < playerCount; seat++) {
            boolean botForElo = isBot[seat] && !abandonedForElo[seat];
            seats.add(new RatingService.SeatRating(seat, userIds[seat], botForElo, placement[seat]));
        }
        return seats;
    }

    /**
     * Ranked game cancelled because no human is connected anymore: before closing the room, we
     * apply the ELO penalty (leavers forced to last). Best-effort, idempotent via {@code recorded}.
     */
    public synchronized void recordAbandonmentForfeit() {
        cancelTurnTimer();
        if (ratingService == null || !"ranked".equals(mode) || !game.isStarted() || recorded) {
            return;
        }
        recorded = true;
        try {
            broadcastRankedResult(ratingService.applyRankedResult(game.gameType(), eloSeats()));
        } catch (Exception e) {
            // best-effort: a recording failure must not block the room from closing
            LOG.warn("forfeit ELO update failed for room {} (best-effort, room still closes)", id, e);
        }
    }

    private void broadcastRankedResult(List<RatingService.RatingUpdate> updates) {
        List<Map<String, Object>> entries = new ArrayList<>(updates.size());
        for (RatingService.RatingUpdate u : updates) {
            entries.add(Map.of(
                    "seat", u.seat(),
                    "ratingBefore", u.before(),
                    "ratingAfter", u.after(),
                    "delta", u.delta()));
        }
        broadcastRaw(Map.of("type", "rankedResult", "entries", entries));
        for (RatingService.RatingUpdate u : updates) {
            if (userIds[u.seat()] != null) {
                broadcastSystemChat(u.seat(), eloChatLine(names[u.seat()], u.before(), u.after(), u.delta()));
            }
        }
    }

    public synchronized GameStateMessage viewFor(int seat) {
        boolean finished = game.isStarted() && (stopped || game.isComplete());
        boolean live = game.isStarted() && !finished;
        VoteState stopVote = live && voteOpen
                ? new VoteState(true, humanSeatCount(), yesVotes(votes), isBot[seat] ? null : votes[seat])
                : null;
        PauseVoteState pauseVote = live && pauseVoteOpen
                ? new PauseVoteState(
                        true, humanSeatCount(), yesVotes(pauseVotes), isBot[seat] ? null : pauseVotes[seat])
                : null;
        PausedState pausedState = live && paused ? new PausedState(true, pauseEndsAtMs) : null;
        OptionalInt actor = game.currentActor();
        Long turnDeadline = turnDeadlineEpochMs > 0 && actor.isPresent() && actor.getAsInt() == turnTimeoutSeat
                ? turnDeadlineEpochMs
                : null;
        return game.view(new RoomContext(
                id,
                playerCount,
                seat,
                resumeTokens[seat],
                phase(),
                playersInfo(),
                names.clone(),
                finished,
                stopVote,
                pauseVote,
                pausedState,
                turnDeadline));
    }

    private String phase() {
        if (!game.isStarted()) {
            return "LOBBY";
        }
        if (stopped || game.isComplete()) {
            return "GAME_OVER";
        }
        return game.betweenRounds() ? "CONTRACT_SELECTION" : "PLAYING";
    }

    private List<PlayerInfo> playersInfo() {
        List<PlayerInfo> players = new ArrayList<>();
        for (int seat = 0; seat < playerCount; seat++) {
            // A seat is open (empty name) only if never claimed; a disconnected human keeps a resume
            // token, so we still surface their name and let the client render them as disconnected.
            boolean member = isBot[seat] || sessions[seat] != null || resumeTokens[seat] != null;
            String name = member && names[seat] != null ? names[seat] : "";
            boolean connected = sessions[seat] != null && sessions[seat].isOpen();
            players.add(new PlayerInfo(seat, name, isBot[seat], connected));
        }
        return players;
    }
}
