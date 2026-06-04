package com.barbu.app.room;

import com.barbu.app.protocol.Codec;
import com.barbu.bot.BotStrategy;
import com.barbu.bot.HeuristicBot;
import com.barbu.engine.card.Card;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.MontanteState;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.Trick;
import com.barbu.engine.round.TrickTakingState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.websocket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One game table held in a single pod's memory. All mutations are serialized on the
 * room monitor; bot turns are paced through a scheduler so a human watches them unfold.
 */
public final class GameRoom {

    private final String id;
    private final int playerCount;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final long botDelayMs;
    private final BotStrategy bot = new HeuristicBot();

    private final String[] names;
    private final boolean[] isBot;
    private final WebSocketSession[] sessions;

    private MatchState match;

    GameRoom(String id, int playerCount, ObjectMapper mapper, ScheduledExecutorService scheduler, long botDelayMs) {
        this.id = id;
        this.playerCount = playerCount;
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.botDelayMs = botDelayMs;
        this.names = new String[playerCount];
        this.isBot = new boolean[playerCount];
        this.sessions = new WebSocketSession[playerCount];
    }

    public String id() {
        return id;
    }

    public synchronized int addHuman(WebSocketSession session, String name) {
        int seat = firstFreeSeat();
        if (seat < 0) {
            return -1;
        }
        sessions[seat] = session;
        names[seat] = name == null || name.isBlank() ? "Player " + seat : name;
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
        match = MatchEngine.newMatch(playerCount, seed);
        broadcast();
        scheduleBotsIfNeeded();
        return true;
    }

    public synchronized void chooseContract(int seat, Contract contract) {
        if (match == null || match.round() != null || seat != match.dealer() || isBot[seat]) {
            return;
        }
        match = MatchEngine.chooseContract(match, contract);
        broadcast();
        scheduleBotsIfNeeded();
    }

    public synchronized void play(int seat, Move move) {
        if (match == null || match.round() == null || seat != match.round().currentPlayer() || isBot[seat]) {
            return;
        }
        match = MatchEngine.applyMove(match, seat, move);
        broadcast();
        scheduleBotsIfNeeded();
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
        if (match == null || MatchEngine.isComplete(match)) {
            return false;
        }
        int actor = match.round() == null ? match.dealer() : match.round().currentPlayer();
        return isBot[actor];
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
        if (match.round() == null) {
            match = MatchEngine.chooseContract(match, bot.chooseContract(match));
        } else {
            int seat = match.round().currentPlayer();
            match = MatchEngine.applyMove(match, seat, bot.chooseMove(match.round(), seat));
        }
        broadcast();
        scheduleBotsIfNeeded();
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
    }

    public synchronized Map<String, Object> viewFor(int seat) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "state");
        view.put("roomId", id);
        view.put("playerCount", playerCount);
        view.put("yourSeat", seat);
        view.put("phase", phase());
        view.put("players", playersInfo());

        if (match == null) {
            return view;
        }

        view.put("dealer", match.dealer());
        view.put("roundNumber", match.roundNumber());
        view.put("plannedRounds", match.plannedRounds());
        view.put("totals", toList(match.totals()));

        if (MatchEngine.isComplete(match)) {
            view.put("standings", standings());
            return view;
        }

        RoundState round = match.round();
        if (round == null) {
            view.put("currentActor", match.dealer());
            if (seat == match.dealer()) {
                view.put("availableContracts", availableContracts());
            }
            return view;
        }

        view.put("currentActor", round.currentPlayer());
        view.put("contract", round.contract().name());
        view.put("handCounts", handCounts(round));
        view.put("yourHand", handMaps(handsOf(round).get(seat)));

        if (round instanceof TrickTakingState t) {
            view.put("trick", trickView(t.currentTrick()));
        } else if (round instanceof MontanteState m) {
            view.put("board", Codec.boardToMap(m.board()));
        }

        if (round.currentPlayer() == seat) {
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
        if (MatchEngine.isComplete(match)) {
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

    private List<String> availableContracts() {
        List<String> contracts = new ArrayList<>();
        for (Contract contract : Contract.values()) {
            if (!match.playedByDealer().contains(contract)) {
                contracts.add(contract.name());
            }
        }
        return contracts;
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
