package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.protocol.ChatBroadcast;
import com.barbu.app.protocol.ChatFilter;
import com.barbu.app.protocol.Codec;
import com.barbu.bot.BotStrategy;
import com.barbu.bot.HeuristicBot;
import com.barbu.engine.card.Card;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
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
    private final BotStrategy bot = new HeuristicBot();

    private static final long VOTE_TIMEOUT_MS = 15000;

    private static final int MAX_CHAT_LEN = 280;
    private static final long CHAT_MIN_INTERVAL_MS = 500;

    private final String[] names;
    private final boolean[] isBot;
    private final Long[] userIds;
    private final WebSocketSession[] sessions;
    private final long[] lastChatAt;
    private final ChatFilter chatFilter = new ChatFilter();

    private MatchState match;
    private boolean recorded;
    private boolean stopped;
    private boolean trickResolving;
    private boolean voteOpen;
    private Boolean[] votes;
    private ScheduledFuture<?> voteTimeout;

    GameRoom(
            String id,
            int playerCount,
            Variant variant,
            ObjectMapper mapper,
            ScheduledExecutorService scheduler,
            long botDelayMs,
            MatchRecorder recorder,
            com.barbu.app.metrics.GameMetrics metrics) {
        this.id = id;
        this.playerCount = playerCount;
        this.variant = variant;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.botDelayMs = botDelayMs;
        this.recorder = recorder;
        this.metrics = metrics;
        this.names = new String[playerCount];
        this.isBot = new boolean[playerCount];
        this.userIds = new Long[playerCount];
        this.sessions = new WebSocketSession[playerCount];
        this.lastChatAt = new long[playerCount];
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

    /** A human left: hand their seat to a bot so the table keeps playing (spec §5.5). */
    public synchronized void handleDisconnect(int seat) {
        if (seat < 0 || seat >= playerCount) {
            return;
        }
        sessions[seat] = null;
        if (match != null) {
            isBot[seat] = true;
            broadcast();
            scheduleBotsIfNeeded();
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
            scheduleBotsIfNeeded();
        }
    }

    private synchronized void startContractStep() {
        if (stopped || voteOpen || match == null || match.round() != null || MatchEngine.isComplete(match)) {
            return;
        }
        match = MatchEngine.startNextContract(match);
        broadcast();
        scheduleBotsIfNeeded();
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
        scheduleBotsIfNeeded();
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
        return match != null && match.round() != null && match.round().isComplete();
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
            recorder.record("private", match, players);
        } catch (Exception ignored) {
            // persistence is best-effort; a failure must not interrupt play
        }
    }

    public synchronized Map<String, Object> viewFor(int seat) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "state");
        view.put("roomId", id);
        view.put("playerCount", playerCount);
        view.put("yourSeat", seat);
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
        view.put("contract", round.contract().name());
        view.put("handCounts", handCounts(round));
        view.put("yourHand", handMaps(handsOf(round).get(seat)));

        if (round instanceof TrickTakingState t) {
            view.put("trick", trickView(t.currentTrick()));
            int[] running = match.variant()
                    .trickRules()
                    .get(t.contract())
                    .score(new TrickOutcome(t.captured(), t.trickTakers(), playerCount));
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
