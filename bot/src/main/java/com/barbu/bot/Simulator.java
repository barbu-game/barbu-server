package com.barbu.bot;

import com.barbu.engine.match.MatchEngine;
import com.barbu.engine.match.MatchState;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import java.util.List;

/** Plays full bot-vs-bot matches; runnable as a CLI for quick manual inspection. */
public final class Simulator {
    private Simulator() {}

    public static MatchState play(int playerCount, long seed, BotStrategy bot) {
        return play(playerCount, seed, bot, Variants.DEVELOPER);
    }

    public static MatchState play(int playerCount, long seed, BotStrategy bot, Variant variant) {
        MatchState match = MatchEngine.newMatch(playerCount, seed, variant);
        while (!MatchEngine.isComplete(match)) {
            match = MatchEngine.startNextContract(match);
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
        Variant variant =
                args.length > 2 && Variants.byId(args[2]) != null ? Variants.byId(args[2]) : Variants.DEVELOPER;

        MatchState match = play(playerCount, seed, new HeuristicBot(), variant);

        System.out.printf(
                "Barbu — %s — %d players, seed %d, %d rounds%n", variant.id(), playerCount, seed, match.roundNumber());
        List<Integer> standings = MatchEngine.standings(match);
        for (int rank = 0; rank < standings.size(); rank++) {
            int seat = standings.get(rank);
            System.out.printf("  #%d  seat %d  %d pts%n", rank + 1, seat, match.totals()[seat]);
        }
    }
}
