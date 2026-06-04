package com.barbu.bot;

import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.round.RoundState;

import java.util.List;

/** Plays full bot-vs-bot matches; runnable as a CLI for quick manual inspection. */
public final class Simulator {
    private Simulator() {
    }

    public static MatchState play(int playerCount, long seed, BotStrategy bot) {
        MatchState match = MatchEngine.newMatch(playerCount, seed);
        while (!MatchEngine.isComplete(match)) {
            match = MatchEngine.chooseContract(match, bot.chooseContract(match));
            while (match.round() != null) {
                RoundState round = match.round();
                int seat = round.currentPlayer();
                match = MatchEngine.applyMove(match, seat, bot.chooseMove(round, seat));
            }
        }
        return match;
    }

    public static void main(String[] args) {
        int playerCount = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;

        MatchState match = play(playerCount, seed, new HeuristicBot());

        System.out.printf("Barbu — %d players, seed %d, %d rounds%n",
                playerCount, seed, match.roundNumber());
        List<Integer> standings = MatchEngine.standings(match);
        for (int rank = 0; rank < standings.size(); rank++) {
            int seat = standings.get(rank);
            System.out.printf("  #%d  seat %d  %d pts%n", rank + 1, seat, match.totals()[seat]);
        }
    }
}
