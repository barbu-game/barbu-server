package com.barbu.rami.match;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.round.Phase;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import com.barbu.rami.round.RamiRoundEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public final class RamiMatchEngine {
    private RamiMatchEngine() {}

    public static RamiMatch newMatch(int playerCount, long seed) {
        return newMatch(playerCount, seed, playerCount);
    }

    public static RamiMatch newMatch(int playerCount, long seed, int totalRounds) {
        if (playerCount < RamiRoundEngine.MIN_PLAYERS || playerCount > RamiRoundEngine.MAX_PLAYERS) {
            throw new IllegalArgumentException("playerCount out of range: " + playerCount);
        }
        if (totalRounds < 1) {
            throw new IllegalArgumentException("totalRounds must be positive: " + totalRounds);
        }
        return new RamiMatch(playerCount, totalRounds, seed, 0, Collections.nCopies(playerCount, 0), null, null);
    }

    public static RamiMatch startNextRound(RamiMatch m) {
        if (m.round() != null) {
            throw new IllegalStateException("a round is already in progress");
        }
        if (m.isComplete()) {
            throw new IllegalStateException("match is over");
        }
        int number = m.roundNumber() + 1;
        int dealer = (number - 1) % m.playerCount();
        RamiRound round = RamiRoundEngine.deal(m.playerCount(), dealer, m.seed() * 31 + number);
        return new RamiMatch(m.playerCount(), m.totalRounds(), m.seed(), number, m.totals(), round, m.lastRound());
    }

    public static RamiMatch apply(RamiMatch m, int seat, RamiMove move) {
        if (m.round() == null) {
            throw new IllegalStateException("no round in progress");
        }
        RamiRound round = RamiRoundEngine.apply(m.round(), seat, move);
        if (round.phase() != Phase.FINISHED) {
            return new RamiMatch(
                    m.playerCount(), m.totalRounds(), m.seed(), m.roundNumber(), m.totals(), round, m.lastRound());
        }
        RoundSummary summary = settle(round);
        List<Integer> totals = new ArrayList<>(m.totals());
        for (int s = 0; s < totals.size(); s++) {
            totals.set(s, totals.get(s) + summary.penalties().get(s));
        }
        return new RamiMatch(
                m.playerCount(), m.totalRounds(), m.seed(), m.roundNumber(), List.copyOf(totals), null, summary);
    }

    public static RoundSummary settle(RamiRound round) {
        int factor = round.ramiSec() ? 2 : 1;
        List<Integer> penalties = IntStream.range(0, round.playerCount())
                .mapToObj(s -> factor
                        * round.hand(s).stream().mapToInt(RamiCard::penalty).sum())
                .toList();
        return new RoundSummary(round.winner(), round.ramiSec(), penalties);
    }

    /** Seats from best (lowest total) to worst; ties keep seat order. */
    public static List<Integer> standings(RamiMatch m) {
        return IntStream.range(0, m.playerCount())
                .boxed()
                .sorted(Comparator.comparingInt((Integer s) -> m.totals().get(s))
                        .thenComparingInt(s -> s))
                .toList();
    }
}
