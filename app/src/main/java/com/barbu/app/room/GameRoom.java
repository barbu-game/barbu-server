package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.protocol.ChatBroadcast;
import com.barbu.app.protocol.ChatFilter;
import com.barbu.app.protocol.Codec;
import com.barbu.app.rating.RatingService;
import com.barbu.bot.BotStrategy;
import com.barbu.bot.HeuristicBot;
import com.barbu.engine.card.Card;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.DefaultMove;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.MontanteState;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundResult;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.Trick;
import com.barbu.engine.round.TrickTakingState;
import com.barbu.engine.scoring.TrickOutcome;
import com.barbu.engine.variant.Variant;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.websocket.WebSocketSession;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One game table held in a single pod's memory. All mutations are serialized on the
 * room monitor; bot turns are paced through a scheduler so a human watches them unfold.
 */
public final class GameRoom {

    private final String id;
    private final int playerCount;
    private final Variant variant;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final long botDelayMs;
    private final MatchRecorder recorder;
    private final com.barbu.app.metrics.GameMetrics metrics;
    private final String mode;
    private final RatingService ratingService;
    private final BotStrategy bot = new HeuristicBot();

    private static final long VOTE_TIMEOUT_MS = 15000;

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

    private MatchState match;
    private boolean recorded;
    private boolean stopped;
    private boolean trickResolving;
    private boolean voteOpen;
    private Boolean[] votes;
    private ScheduledFuture<?> voteTimeout;

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
        this.id = id;
        this.playerCount = playerCount;
        this.variant = variant;
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
        int seat = firstFreeSeat();
        if (seat < 0) {
            return -1;
        }
        sessions[seat] = session;
        names[seat] = name == null || name.isBlank() ? "Player " + seat : name;
        userIds[seat] = userId;
        isBot[seat] = false;
        resumeTokens[seat] = UUID.randomUUID().toString();
        if (reconnectIndex != null) {
            reconnectIndex.register(userId, resumeTokens[seat], id);
        }
        return seat;
    }

    public synchronized boolean addBot() {
        if (match != null) {
            return false;
        }
        int seat = firstFreeSeat();
        if (seat < 0) {
            return false;
        }
        isBot[seat] = true;
        names[seat] = "Bot " + seat;
        return true;
    }

    public synchronized boolean start(long seed) {
        if (match != null || !isFull()) {
            return false;
        }
        match = MatchEngine.newMatch(playerCount, seed, variant);
        if (metrics != null) {
            metrics.gameStarted();
        }
        broadcast();
        resume();
        return true;
    }

    public synchronized void play(int seat, Move move) {
        if (voteOpen
                || trickResolving
                || match == null
                || match.round() == null
                || seat != match.round().currentPlayer()
                || isBot[seat]) {
            return;
        }
        strikes[seat] = 0;
        cancelTurnTimer();
        match = MatchEngine.applyMoveNoSettle(match, seat, move);
        afterAdvance();
    }

    /** A human votes at a dealer boundary: true = stop the game here, false = keep playing. */
    public synchronized void castStopVote(int seat, boolean stop) {
        if (!voteOpen || seat < 0 || seat >= playerCount || isBot[seat]) {
            return;
        }
        votes[seat] = stop;
        if (allHumansVoted()) {
            resolveVote();
        } else {
            broadcast();
        }
    }

    /** Un humain attablé écrit dans le tchat de table. Diffusé à tous, jamais persisté. */
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
                "ts", m.ts()));
    }

    static boolean stopVotePasses(int stopVotes, int humans) {
        return stopVotes * 2 > humans;
    }

    /**
     * Index du siège déconnecté que ce revenant peut reprendre, ou -1. Le token (capacité d'invité)
     * prime sur le userId. {@code occupied[s]} = une session est déjà active sur le siège.
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
     * Décide d'un message de tchat sans aucun effet de bord : renvoie le {@link ChatBroadcast}
     * à diffuser, ou vide si le message est ignoré (siège bot, texte vide, ou anti-spam).
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
        return Optional.of(new ChatBroadcast(seat, name, clean, now));
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
        if (match != null) {
            broadcast();
        }
    }

    /**
     * Un revenant reprend son siège : rebind la session, lève le bot, diffuse. Renvoie le siège
     * repris, ou -1 si aucun siège déconnecté ne lui appartient.
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
        sessions[seat] = session;
        isBot[seat] = false;
        abandoned[seat] = false;
        strikes[seat] = 0;
        broadcast();
        return seat;
    }

    /** Purge les entrées d'index de tous les sièges (appelé à la destruction de la room). */
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

    private boolean currentActorIsBot() {
        if (stopped
                || voteOpen
                || trickResolving
                || match == null
                || MatchEngine.isComplete(match)
                || match.round() == null) {
            return false;
        }
        return isBot[match.round().currentPlayer()];
    }

    /** Drive the table forward: open the next imposed contract, or let a waiting bot act. */
    private void resume() {
        if (stopped || voteOpen || match == null || MatchEngine.isComplete(match)) {
            return;
        }
        if (match.round() == null) {
            // Pause on an empty table so the round-switch recap can be shown before the next deal.
            scheduler.schedule(this::startContractStep, roundSwitchPauseMs(), TimeUnit.MILLISECONDS);
        } else {
            scheduleActor();
        }
    }

    private synchronized void startContractStep() {
        if (stopped || voteOpen || match == null || match.round() != null || MatchEngine.isComplete(match)) {
            return;
        }
        match = MatchEngine.startNextContract(match);
        broadcast();
        scheduleActor();
    }

    private void afterAdvance() {
        if (roundJustCompleted()) {
            broadcast();
            // Let everyone see the final trick (who took it) before scoring the round.
            scheduler.schedule(this::settleStep, roundEndPauseMs(), TimeUnit.MILLISECONDS);
        } else if (trickPending()) {
            // Hold the finished trick on the table; nobody acts until it is collected.
            trickResolving = true;
            broadcast();
            scheduler.schedule(this::releaseTrick, trickPauseMs(), TimeUnit.MILLISECONDS);
        } else {
            broadcast();
            resume();
        }
    }

    private synchronized void releaseTrick() {
        if (!trickResolving) {
            return;
        }
        trickResolving = false;
        match = MatchEngine.collectTrick(match);
        broadcast();
        scheduleActor();
    }

    private synchronized void settleStep() {
        if (!roundJustCompleted()) {
            return;
        }
        match = MatchEngine.settle(match);
        broadcast();
        if (isVotableBoundary()) {
            openVote();
        } else {
            resume();
        }
    }

    private boolean roundJustCompleted() {
        return match != null && MatchEngine.roundOver(match);
    }

    /** A trick is finished but still displayed; the taker has not led the next one yet. */
    private boolean trickPending() {
        return match != null
                && match.round() instanceof TrickTakingState t
                && t.currentTrick().isComplete()
                && !t.isComplete();
    }

    private long trickPauseMs() {
        return botDelayMs == 0 ? 0 : 2000;
    }

    private long roundEndPauseMs() {
        return botDelayMs == 0 ? 0 : 2500;
    }

    private long roundSwitchPauseMs() {
        return botDelayMs == 0 ? 0 : 3500;
    }

    private boolean isVotableBoundary() {
        return !voteOpen
                && !stopped
                && match != null
                && !MatchEngine.isComplete(match)
                && MatchEngine.isDealerBoundary(match)
                && humanSeatCount() > 0;
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

    private boolean allHumansVoted() {
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && votes[seat] == null) {
                return false;
            }
        }
        return true;
    }

    private int stopVoteCount() {
        int yes = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && Boolean.TRUE.equals(votes[seat])) {
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
        if (stopVotePasses(stopVoteCount(), humanSeatCount())) {
            stopped = true;
            broadcast();
        } else {
            broadcast();
            resume();
        }
    }

    private void scheduleBotsIfNeeded() {
        if (currentActorIsBot()) {
            scheduler.schedule(this::botStep, botDelayMs, TimeUnit.MILLISECONDS);
        }
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
        if (stopped || voteOpen || trickResolving || match == null) {
            return;
        }
        RoundState round = match.round();
        if (round == null || MatchEngine.isComplete(match)) {
            return;
        }
        int seat = round.currentPlayer();
        if (isBot[seat]) {
            return;
        }
        turnTimeoutSeat = seat;
        turnDeadlineEpochMs = System.currentTimeMillis() + turnTimeoutMs;
        turnTimeout = scheduler.schedule(() -> onTurnTimeout(seat), turnTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void cancelTurnTimer() {
        if (turnTimeout != null) {
            turnTimeout.cancel(false);
            turnTimeout = null;
        }
        turnTimeoutSeat = -1;
        turnDeadlineEpochMs = 0;
    }

    private synchronized void onTurnTimeout(int seat) {
        RoundState round = match == null ? null : match.round();
        if (stopped
                || voteOpen
                || trickResolving
                || round == null
                || MatchEngine.isComplete(match)
                || round.currentPlayer() != seat
                || isBot[seat]) {
            return;
        }
        match = MatchEngine.applyMoveNoSettle(match, seat, DefaultMove.pick(RoundEngine.legalMoves(round, seat)));
        if (++strikes[seat] >= turnTimeoutStrikes) {
            isBot[seat] = true;
            abandoned[seat] = true;
        }
        afterAdvance();
    }

    private synchronized void botStep() {
        if (!currentActorIsBot()) {
            return;
        }
        int seat = match.round().currentPlayer();
        match = MatchEngine.applyMoveNoSettle(match, seat, bot.chooseMove(match.round(), seat));
        afterAdvance();
    }

    private int firstFreeSeat() {
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && sessions[seat] == null) {
                return seat;
            }
        }
        return -1;
    }

    private boolean isFull() {
        for (int seat = 0; seat < playerCount; seat++) {
            if (!isBot[seat] && sessions[seat] == null) {
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
                session.sendSync(mapper.writeValueAsString(viewFor(seat)));
            } catch (Exception ignored) {
                // a dropped client is reconciled on its next connect via a fresh snapshot
            }
        }
        maybeRecord();
    }

    /** Envoie le même payload à toutes les sessions ouvertes (pas de rédaction par siège). */
    private synchronized void broadcastRaw(Map<String, Object> payload) {
        for (int seat = 0; seat < playerCount; seat++) {
            WebSocketSession session = sessions[seat];
            if (session == null || !session.isOpen()) {
                continue;
            }
            try {
                session.sendSync(mapper.writeValueAsString(payload));
            } catch (Exception ignored) {
                // a dropped client is reconciled on its next snapshot; chat is best-effort
            }
        }
    }

    private void maybeRecord() {
        if (recorder == null || recorded || match == null || !(stopped || MatchEngine.isComplete(match))) {
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
            recorder.record(mode, match, players);
        } catch (Exception ignored) {
            // persistence is best-effort; a failure must not interrupt play
        }

        if (ratingService != null && "ranked".equals(mode) && MatchEngine.isComplete(match)) {
            try {
                broadcastRankedResult(ratingService.applyRankedResult(eloSeats()));
            } catch (Exception ignored) {
                // l'ELO est best-effort : un échec ne doit pas interrompre la fin de partie
            }
        }
    }

    /**
     * Place chaque siège non abandonné selon l'ordre de classement (score), puis range tous les
     * abandonnés à la même dernière place ex æquo — « un partant a forcément perdu ».
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

    /** Un compte humain exclu (strikes) ou déconnecté est traité comme un partant pour l'ELO. */
    private boolean[] abandonedForElo() {
        boolean[] flags = new boolean[playerCount];
        for (int seat = 0; seat < playerCount; seat++) {
            flags[seat] = userIds[seat] != null && (abandoned[seat] || sessions[seat] == null);
        }
        return flags;
    }

    private List<RatingService.SeatRating> eloSeats() {
        boolean[] abandonedForElo = abandonedForElo();
        int[] placement = placementsForElo(MatchEngine.standings(match), abandonedForElo);
        List<RatingService.SeatRating> seats = new ArrayList<>(playerCount);
        for (int seat = 0; seat < playerCount; seat++) {
            boolean botForElo = isBot[seat] && !abandonedForElo[seat];
            seats.add(new RatingService.SeatRating(seat, userIds[seat], botForElo, placement[seat]));
        }
        return seats;
    }

    /**
     * Partie ranked annulée parce que plus aucun humain n'est connecté : avant de fermer la room,
     * on applique le malus ELO (partants forcés en dernier). Best-effort, idempotent via {@code recorded}.
     */
    public synchronized void recordAbandonmentForfeit() {
        cancelTurnTimer();
        if (ratingService == null || !"ranked".equals(mode) || match == null || recorded) {
            return;
        }
        recorded = true;
        try {
            broadcastRankedResult(ratingService.applyRankedResult(eloSeats()));
        } catch (Exception ignored) {
            // best-effort : l'échec d'enregistrement ne doit pas bloquer la fermeture de la room
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
    }

    public synchronized Map<String, Object> viewFor(int seat) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "state");
        view.put("roomId", id);
        view.put("playerCount", playerCount);
        view.put("yourSeat", seat);
        if (resumeTokens[seat] != null) {
            view.put("resumeToken", resumeTokens[seat]);
        }
        view.put("phase", phase());
        view.put("players", playersInfo());

        Map<String, Object> variantInfo = new LinkedHashMap<>();
        variantInfo.put("id", variant.id());
        variantInfo.put("name", variant.name());
        view.put("variant", variantInfo);

        if (match == null) {
            return view;
        }

        view.put("dealer", match.dealer());
        view.put("roundNumber", match.roundNumber());
        view.put("plannedRounds", match.plannedRounds());
        view.put("totals", toList(match.totals()));

        if (stopped || MatchEngine.isComplete(match)) {
            view.put("standings", standings());
            return view;
        }

        if (voteOpen) {
            Map<String, Object> vote = new LinkedHashMap<>();
            vote.put("open", true);
            vote.put("humans", humanSeatCount());
            vote.put("stopVotes", stopVoteCount());
            vote.put("youVoted", isBot[seat] ? null : votes[seat]);
            view.put("stopVote", vote);
        }

        RoundState round = match.round();
        if (round == null) {
            view.put("currentActor", match.dealer());
            view.put("nextContract", MatchEngine.nextContract(match).name());
            if (!match.history().isEmpty()) {
                view.put("lastRound", lastRoundView());
            }
            return view;
        }

        view.put("currentActor", round.currentPlayer());
        if (turnDeadlineEpochMs > 0 && round.currentPlayer() == turnTimeoutSeat) {
            view.put("turnDeadlineEpochMs", turnDeadlineEpochMs);
        }
        view.put("contract", round.contract().name());
        view.put("handCounts", handCounts(round));
        view.put("yourHand", handMaps(handsOf(round).get(seat)));

        if (round instanceof TrickTakingState t) {
            view.put("trick", trickView(t.currentTrick()));
            int[] running = match.variant()
                    .trickRules()
                    .get(t.contract())
                    .runningScore(new TrickOutcome(t.captured(), t.trickTakers(), playerCount), notYetCaptured(t));
            List<Integer> roundScores = new ArrayList<>();
            for (int s = 0; s < playerCount; s++) {
                roundScores.add(running[s]);
            }
            view.put("roundScores", roundScores);
        } else if (round instanceof MontanteState m) {
            view.put("board", Codec.boardToMap(m.board()));
        }

        if (trickResolving) {
            view.put("resolving", true);
        } else if (round.currentPlayer() == seat) {
            List<Map<String, Object>> moves = new ArrayList<>();
            for (Move move : RoundEngine.legalMoves(round, seat)) {
                moves.add(Codec.moveToMap(move));
            }
            view.put("yourLegalMoves", moves);
        }
        return view;
    }

    /**
     * Every card not yet in a captured pile, grouped by seat: each hand plus the card that seat has
     * already laid into the in-progress trick. Restoring those laid cards keeps each seat's count at
     * its true remaining-tricks value and exposes every still-uncaptured penalty card, so the running
     * score spreads each contract's points over the round's full unit count rather than flickering as
     * the current trick fills and resolves.
     */
    private static List<List<Card>> notYetCaptured(TrickTakingState t) {
        List<List<Card>> remaining = new ArrayList<>();
        for (List<Card> hand : t.hands()) {
            remaining.add(new ArrayList<>(hand));
        }
        Trick trick = t.currentTrick();
        for (int i = 0; i < trick.cards().size(); i++) {
            remaining.get(trick.playerAt(i)).add(trick.cards().get(i));
        }
        return remaining;
    }

    private String phase() {
        if (match == null) {
            return "LOBBY";
        }
        if (stopped || MatchEngine.isComplete(match)) {
            return "GAME_OVER";
        }
        return match.round() == null ? "CONTRACT_SELECTION" : "PLAYING";
    }

    private List<Map<String, Object>> playersInfo() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int seat = 0; seat < playerCount; seat++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("seat", seat);
            p.put("name", names[seat] == null ? "Empty" : names[seat]);
            p.put("bot", isBot[seat]);
            p.put("connected", sessions[seat] != null && sessions[seat].isOpen());
            players.add(p);
        }
        return players;
    }

    /** Recap of the just-finished round: who scored what, ranked on that round alone. */
    private Map<String, Object> lastRoundView() {
        List<RoundResult> history = match.history();
        RoundResult last = history.get(history.size() - 1);
        int[] points = last.points();

        List<Integer> order = new ArrayList<>();
        for (int s = 0; s < playerCount; s++) {
            order.add(s);
        }
        order.sort(Comparator.comparingInt((Integer s) -> points[s]).reversed());

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (int rank = 0; rank < order.size(); rank++) {
            int s = order.get(rank);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank + 1);
            row.put("seat", s);
            row.put("name", names[s]);
            row.put("points", points[s]);
            ranking.add(row);
        }

        Map<String, Object> recap = new LinkedHashMap<>();
        recap.put("contract", last.contract().name());
        recap.put("ranking", ranking);
        return recap;
    }

    private List<Map<String, Object>> standings() {
        List<Map<String, Object>> standings = new ArrayList<>();
        List<Integer> order = MatchEngine.standings(match);
        for (int rank = 0; rank < order.size(); rank++) {
            int seat = order.get(rank);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank + 1);
            row.put("seat", seat);
            row.put("name", names[seat]);
            row.put("total", match.totals()[seat]);
            standings.add(row);
        }
        return standings;
    }

    private Map<String, Object> trickView(Trick trick) {
        List<Map<String, Object>> plays = new ArrayList<>();
        for (int i = 0; i < trick.cards().size(); i++) {
            Map<String, Object> play = new LinkedHashMap<>();
            play.put("seat", trick.playerAt(i));
            play.put("card", Codec.cardToMap(trick.cards().get(i)));
            plays.add(play);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("leader", trick.leader());
        view.put("plays", plays);
        if (trick.isComplete()) {
            view.put("complete", true);
            view.put("taker", trick.taker());
        }
        return view;
    }

    private static List<List<Card>> handsOf(RoundState round) {
        return switch (round) {
            case TrickTakingState t -> t.hands();
            case MontanteState m -> m.hands();
        };
    }

    private static List<Integer> handCounts(RoundState round) {
        List<Integer> counts = new ArrayList<>();
        for (List<Card> hand : handsOf(round)) {
            counts.add(hand.size());
        }
        return counts;
    }

    private static List<Map<String, Object>> handMaps(List<Card> hand) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Card card : hand) {
            cards.add(Codec.cardToMap(card));
        }
        return cards;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values) {
            list.add(v);
        }
        return list;
    }
}
