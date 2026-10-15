package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.protocol.Codec;
import com.barbu.app.protocol.GameStateMessage;
import com.barbu.app.protocol.GameStateMessage.BoardCell;
import com.barbu.app.protocol.GameStateMessage.CardView;
import com.barbu.app.protocol.GameStateMessage.LastRound;
import com.barbu.app.protocol.GameStateMessage.MoveView;
import com.barbu.app.protocol.GameStateMessage.PauseVoteState;
import com.barbu.app.protocol.GameStateMessage.PausedState;
import com.barbu.app.protocol.GameStateMessage.Standing;
import com.barbu.app.protocol.GameStateMessage.TrickView;
import com.barbu.app.protocol.GameStateMessage.VariantInfo;
import com.barbu.app.protocol.GameStateMessage.VoteState;
import com.barbu.bot.BotStrategy;
import com.barbu.bot.HeuristicBot;
import com.barbu.engine.card.Card;
import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.DefaultMove;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.MontanteRules;
import com.barbu.engine.round.MontanteState;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundResult;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.Trick;
import com.barbu.engine.round.TrickTakingState;
import com.barbu.engine.scoring.TrickOutcome;
import com.barbu.engine.variant.Variant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** The Barbu rules behind a {@link GameRoom}: engine state, bot, and the per-seat view. */
final class BarbuSession implements GameSession {

    private static final long TRICK_PAUSE_MS = 2000;
    private static final long ROUND_END_PAUSE_MS = 2500;

    private final int playerCount;
    private final Variant variant;
    private final BotStrategy bot = new HeuristicBot();
    private MatchState match;

    BarbuSession(int playerCount, Variant variant, MatchState match) {
        this.playerCount = playerCount;
        this.variant = variant;
        this.match = match;
    }

    @Override
    public String gameType() {
        return "BARBU";
    }

    @Override
    public boolean isStarted() {
        return match != null;
    }

    @Override
    public void start(long seed) {
        match = MatchEngine.newMatch(playerCount, seed, variant);
    }

    @Override
    public boolean isComplete() {
        return match != null && MatchEngine.isComplete(match);
    }

    @Override
    public OptionalInt currentActor() {
        if (match == null || MatchEngine.isComplete(match) || match.round() == null || roundOver() || trickPending()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(match.round().currentPlayer());
    }

    @Override
    public void play(int seat, Map<String, Object> move) {
        Move parsed;
        try {
            parsed = Codec.parseMove(move);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed move", e);
        }
        match = MatchEngine.applyMoveNoSettle(match, seat, parsed);
    }

    @Override
    public void playBot(int seat) {
        match = MatchEngine.applyMoveNoSettle(match, seat, bot.chooseMove(match.round(), seat));
    }

    @Override
    public void playTimeout(int seat) {
        match = MatchEngine.applyMoveNoSettle(
                match, seat, DefaultMove.pick(RoundEngine.legalMoves(match.round(), seat)));
    }

    @Override
    public OptionalLong holdStepMs() {
        if (roundOver()) {
            // Let everyone see the final trick (who took it) before scoring the round.
            return OptionalLong.of(ROUND_END_PAUSE_MS);
        }
        return trickPending() ? OptionalLong.of(TRICK_PAUSE_MS) : OptionalLong.empty();
    }

    @Override
    public void runHoldStep() {
        if (roundOver()) {
            match = MatchEngine.settle(match);
        } else if (trickPending()) {
            match = MatchEngine.collectTrick(match);
        }
    }

    @Override
    public boolean betweenRounds() {
        return match != null && !MatchEngine.isComplete(match) && match.round() == null;
    }

    @Override
    public void startNextRound() {
        match = MatchEngine.startNextContract(match);
    }

    @Override
    public boolean atStopVoteBoundary() {
        return match != null && !MatchEngine.isComplete(match) && MatchEngine.isDealerBoundary(match);
    }

    @Override
    public List<Integer> standings() {
        return MatchEngine.standings(match);
    }

    @Override
    public void record(MatchRecorder recorder, String mode, List<MatchRecorder.PlayerInfo> players) {
        recorder.record(mode, match, players);
    }

    @Override
    public GameSnapshot snapshot(
            String roomId,
            String mode,
            String[] names,
            boolean[] isBot,
            Long[] userIds,
            String[] resumeTokens,
            boolean stopped) {
        return new GameSnapshot(
                roomId, playerCount, variant.id(), mode, names, isBot, userIds, resumeTokens, stopped, match);
    }

    private boolean roundOver() {
        return match != null && MatchEngine.roundOver(match);
    }

    /** A trick is finished but still displayed; the taker has not led the next one yet. */
    private boolean trickPending() {
        return match != null
                && match.round() instanceof TrickTakingState t
                && t.currentTrick().isComplete()
                && !t.isComplete();
    }

    @Override
    public GameStateMessage view(RoomContext ctx) {
        int seat = ctx.seat();
        VariantInfo variantInfo = new VariantInfo(variant.id(), variant.name());

        Integer dealer = null;
        Integer roundNumber = null;
        Integer plannedRounds = null;
        List<Integer> totals = null;
        List<Standing> standings = null;
        VoteState stopVote = null;
        PauseVoteState pauseVote = null;
        PausedState pausedState = null;
        Integer currentActor = null;
        Long turnDeadline = null;
        String contract = null;
        List<Integer> handCounts = null;
        List<CardView> yourHand = null;
        List<Integer> roundScores = null;
        List<List<CardView>> captured = null;
        TrickView trick = null;
        Map<String, BoardCell> board = null;
        Boolean resolving = null;
        String nextContract = null;
        LastRound lastRound = null;
        List<MoveView> yourLegalMoves = null;

        if (match != null) {
            dealer = match.dealer();
            roundNumber = match.roundNumber();
            plannedRounds = match.plannedRounds();
            totals = toList(match.totals());

            if (ctx.finished()) {
                standings = standings(ctx.names());
            } else {
                stopVote = ctx.stopVote();
                pauseVote = ctx.pauseVote();
                pausedState = ctx.paused();

                RoundState round = match.round();
                if (round == null) {
                    currentActor = match.dealer();
                    nextContract = MatchEngine.nextContract(match).name();
                    if (!match.history().isEmpty()) {
                        lastRound = lastRoundView(ctx.names());
                    }
                } else {
                    currentActor = round.currentPlayer();
                    turnDeadline = ctx.turnDeadline();
                    contract = round.contract().name();
                    handCounts = handCounts(round);
                    yourHand = handViews(handsOf(round).get(seat));

                    if (round instanceof TrickTakingState t) {
                        trick = trickView(t.currentTrick());
                        int[] running = match.variant()
                                .trickRules()
                                .get(t.contract())
                                .runningScore(
                                        new TrickOutcome(t.captured(), t.trickTakers(), playerCount),
                                        notYetCaptured(t));
                        List<Integer> rs = new ArrayList<>();
                        for (int s = 0; s < playerCount; s++) {
                            rs.add(running[s]);
                        }
                        roundScores = rs;
                        captured = capturedPerSeat(t);
                    } else if (round instanceof MontanteState m) {
                        board = Codec.boardView(m.board());
                        int[] running = MontanteRules.runningScore(m);
                        List<Integer> rs = new ArrayList<>();
                        for (int s = 0; s < playerCount; s++) {
                            rs.add(running[s]);
                        }
                        roundScores = rs;
                    }

                    // A round can end on a trick before the last one (e.g. all queens taken): no trick hold then.
                    if (trickPending() && !roundOver()) {
                        resolving = true;
                    } else if (round.currentPlayer() == seat) {
                        List<MoveView> moves = new ArrayList<>();
                        for (Move move : RoundEngine.legalMoves(round, seat)) {
                            moves.add(Codec.moveView(move));
                        }
                        yourLegalMoves = moves;
                    }
                }
            }
        }

        return new GameStateMessage(
                "state",
                ctx.roomId(),
                playerCount,
                seat,
                ctx.resumeToken(),
                ctx.phase(),
                ctx.players(),
                variantInfo,
                dealer,
                roundNumber,
                plannedRounds,
                totals,
                standings,
                stopVote,
                pauseVote,
                pausedState,
                currentActor,
                turnDeadline,
                contract,
                handCounts,
                yourHand,
                roundScores,
                captured,
                trick,
                board,
                resolving,
                nextContract,
                lastRound,
                yourLegalMoves,
                "BARBU",
                null);
    }

    /**
     * Every card not yet in a captured pile, grouped by seat: each hand plus the card that seat has
     * already laid into the in-progress trick. Counting those laid cards keeps the running score
     * spread over the round's full unit count instead of flickering as the trick fills and resolves.
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

    /** Recap of the just-finished round: who scored what, ranked on that round alone. */
    private LastRound lastRoundView(String[] names) {
        List<RoundResult> history = match.history();
        RoundResult last = history.get(history.size() - 1);
        int[] points = last.points();

        List<Integer> order = new ArrayList<>();
        for (int s = 0; s < playerCount; s++) {
            order.add(s);
        }
        order.sort(Comparator.comparingInt((Integer s) -> points[s]).reversed());

        List<LastRound.RankRow> ranking = new ArrayList<>();
        for (int rank = 0; rank < order.size(); rank++) {
            int s = order.get(rank);
            ranking.add(new LastRound.RankRow(rank + 1, s, names[s], points[s]));
        }
        return new LastRound(last.contract().name(), ranking);
    }

    private List<Standing> standings(String[] names) {
        List<Standing> standings = new ArrayList<>();
        List<Integer> order = MatchEngine.standings(match);
        for (int rank = 0; rank < order.size(); rank++) {
            int seat = order.get(rank);
            standings.add(new Standing(rank + 1, seat, names[seat], match.totals()[seat]));
        }
        return standings;
    }

    private TrickView trickView(Trick trick) {
        List<TrickView.TrickPlay> plays = new ArrayList<>();
        for (int i = 0; i < trick.cards().size(); i++) {
            plays.add(new TrickView.TrickPlay(
                    trick.playerAt(i), Codec.cardView(trick.cards().get(i))));
        }
        if (trick.isComplete()) {
            return new TrickView(trick.leader(), plays, true, trick.taker());
        }
        return new TrickView(trick.leader(), plays, null, null);
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

    private static List<CardView> handViews(List<Card> hand) {
        List<CardView> cards = new ArrayList<>();
        for (Card card : hand) {
            cards.add(Codec.cardView(card));
        }
        return cards;
    }

    private static List<List<CardView>> capturedPerSeat(TrickTakingState t) {
        List<List<CardView>> out = new ArrayList<>();
        for (List<Card> seatCards : t.captured()) {
            out.add(handViews(seatCards));
        }
        return out;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values) {
            list.add(v);
        }
        return list;
    }
}
