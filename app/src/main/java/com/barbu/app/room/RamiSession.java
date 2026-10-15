package com.barbu.app.room;

import com.barbu.app.persistence.MatchRecorder;
import com.barbu.app.protocol.GameStateMessage;
import com.barbu.app.protocol.GameStateMessage.PauseVoteState;
import com.barbu.app.protocol.GameStateMessage.PausedState;
import com.barbu.app.protocol.GameStateMessage.Standing;
import com.barbu.app.protocol.GameStateMessage.VariantInfo;
import com.barbu.app.protocol.RamiCodec;
import com.barbu.app.protocol.RamiView;
import com.barbu.bot.rami.RamiBot;
import com.barbu.rami.card.RamiCard;
import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RamiMatchEngine;
import com.barbu.rami.round.Phase;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** The Rami 51 rules behind a {@link GameRoom}. Rounds settle inside the engine: no held steps. */
final class RamiSession implements GameSession {

    static final String VARIANT_ID = "rami51";
    private static final VariantInfo VARIANT = new VariantInfo(VARIANT_ID, "Rami 51");

    private final int playerCount;
    private final RamiBot bot = new RamiBot();
    private RamiMatch match;

    RamiSession(int playerCount, RamiMatch match) {
        this.playerCount = playerCount;
        this.match = match;
    }

    @Override
    public String gameType() {
        return "RAMI";
    }

    @Override
    public boolean isStarted() {
        return match != null;
    }

    @Override
    public void start(long seed) {
        match = RamiMatchEngine.newMatch(playerCount, seed);
    }

    @Override
    public boolean isComplete() {
        return match != null && match.isComplete();
    }

    @Override
    public OptionalInt currentActor() {
        return match == null || match.round() == null
                ? OptionalInt.empty()
                : OptionalInt.of(match.round().current());
    }

    @Override
    public void play(int seat, Map<String, Object> move) {
        apply(seat, RamiCodec.parseMove(move));
    }

    @Override
    public void playBot(int seat) {
        apply(seat, bot.chooseMove(match.round(), seat));
    }

    // One strike for the whole turn: undo a pending obligation, draw if needed, shed the drawn card.
    @Override
    public void playTimeout(int seat) {
        RamiRound round = match.round();
        if (round.phase() == Phase.PLAY && !round.mustPlay().isEmpty()) {
            apply(seat, new RamiMove.ResetTurn());
        }
        if (match.round().phase() == Phase.DRAW) {
            apply(seat, new RamiMove.Draw(RamiMove.Source.STOCK));
            if (match.round() == null) {
                return;
            }
        }
        List<RamiCard> hand = match.round().hand(seat);
        apply(seat, new RamiMove.Discard(hand.get(hand.size() - 1).id()));
    }

    private void apply(int seat, RamiMove move) {
        match = RamiMatchEngine.apply(match, seat, move);
    }

    @Override
    public OptionalLong holdStepMs() {
        return OptionalLong.empty();
    }

    @Override
    public void runHoldStep() {}

    @Override
    public boolean betweenRounds() {
        return match != null && !match.isComplete() && match.round() == null;
    }

    @Override
    public void startNextRound() {
        match = RamiMatchEngine.startNextRound(match);
    }

    @Override
    public boolean atStopVoteBoundary() {
        return false;
    }

    @Override
    public List<Integer> standings() {
        return RamiMatchEngine.standings(match);
    }

    @Override
    public void record(MatchRecorder recorder, String mode, List<MatchRecorder.PlayerInfo> players) {
        recorder.recordRami(mode, match, players);
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
                roomId,
                playerCount,
                VARIANT_ID,
                mode,
                names,
                isBot,
                userIds,
                resumeTokens,
                stopped,
                null,
                "RAMI",
                match);
    }

    @Override
    public GameStateMessage view(RoomContext ctx) {
        Integer dealer = null;
        Integer roundNumber = null;
        Integer plannedRounds = null;
        List<Integer> totals = null;
        List<Standing> standings = null;
        PauseVoteState pauseVote = null;
        PausedState paused = null;
        Integer currentActor = null;
        Long turnDeadline = null;
        List<Integer> handCounts = null;
        RamiView rami = null;

        if (match != null) {
            dealer = match.roundNumber() > 0 ? match.dealer() : null;
            roundNumber = match.roundNumber();
            plannedRounds = match.totalRounds();
            totals = match.totals();
            rami = RamiCodec.view(match, ctx.seat());
            if (ctx.finished()) {
                standings = standings(ctx.names());
            } else {
                pauseVote = ctx.pauseVote();
                paused = ctx.paused();
                RamiRound round = match.round();
                if (round != null) {
                    currentActor = round.current();
                    turnDeadline = ctx.turnDeadline();
                    handCounts = round.hands().stream().map(List::size).toList();
                }
            }
        }

        return new GameStateMessage(
                "state",
                ctx.roomId(),
                playerCount,
                ctx.seat(),
                ctx.resumeToken(),
                ctx.phase(),
                ctx.players(),
                VARIANT,
                dealer,
                roundNumber,
                plannedRounds,
                totals,
                standings,
                null,
                pauseVote,
                paused,
                currentActor,
                turnDeadline,
                null,
                handCounts,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "RAMI",
                rami);
    }

    private List<Standing> standings(String[] names) {
        List<Standing> standings = new ArrayList<>();
        List<Integer> order = RamiMatchEngine.standings(match);
        for (int rank = 0; rank < order.size(); rank++) {
            int seat = order.get(rank);
            standings.add(
                    new Standing(rank + 1, seat, names[seat], match.totals().get(seat)));
        }
        return standings;
    }
}
