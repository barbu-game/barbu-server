package com.barbu.bot.rami;

import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RamiMatchEngine;
import com.barbu.rami.round.RamiRound;
import java.util.List;

/** Plays full bot-vs-bot Rami matches; runnable as a CLI for quick manual inspection. */
public final class RamiSimulator {
    public static final int MAX_MOVES_PER_ROUND = 20_000;

    private RamiSimulator() {}

    public static RamiMatch play(int playerCount, long seed, RamiBot bot) {
        RamiMatch match = RamiMatchEngine.newMatch(playerCount, seed);
        while (!match.isComplete()) {
            match = RamiMatchEngine.startNextRound(match);
            for (int moves = 0; match.round() != null; moves++) {
                if (moves > MAX_MOVES_PER_ROUND) {
                    throw new IllegalStateException("round " + match.roundNumber() + " never ended, seed " + seed);
                }
                RamiRound round = match.round();
                int seat = round.current();
                match = RamiMatchEngine.apply(match, seat, bot.chooseMove(round, seat));
            }
        }
        return match;
    }

    public static void main(String[] args) {
        int playerCount = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        RamiMatch match = play(playerCount, seed, new RamiBot());
        System.out.printf("Rami 51 — %d players, seed %d, %d rounds%n", playerCount, seed, match.roundNumber());
        List<Integer> standings = RamiMatchEngine.standings(match);
        for (int rank = 0; rank < standings.size(); rank++) {
            int seat = standings.get(rank);
            System.out.printf(
                    "  #%d  seat %d  %d pts%n", rank + 1, seat, match.totals().get(seat));
        }
    }
}
