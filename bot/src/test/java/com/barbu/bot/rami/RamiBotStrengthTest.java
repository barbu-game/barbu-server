package com.barbu.bot.rami;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RamiMatchEngine;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class RamiBotStrengthTest {

    /** Sum of final totals per side over matches where the two bots alternate seats. Lower is better. */
    static long[] duel(int matches) {
        RamiBot current = new RamiBot();
        BaselineRamiBot baseline = new BaselineRamiBot();
        long[] totals = new long[2];
        for (int g = 0; g < matches; g++) {
            int offset = g % 2;
            BiFunction<RamiRound, Integer, RamiMove> pick = (round, seat) ->
                    (seat + offset) % 2 == 0 ? current.chooseMove(round, seat) : baseline.chooseMove(round, seat);
            RamiMatch m = RamiMatchEngine.newMatch(4, 1_000L + g);
            while (!m.isComplete()) {
                m = RamiMatchEngine.startNextRound(m);
                for (int moves = 0; m.round() != null; moves++) {
                    if (moves > RamiSimulator.MAX_MOVES_PER_ROUND) {
                        throw new IllegalStateException("round never ended, match " + g);
                    }
                    int seat = m.round().current();
                    m = RamiMatchEngine.apply(m, seat, pick.apply(m.round(), seat));
                }
            }
            for (int seat = 0; seat < 4; seat++) {
                totals[(seat + offset) % 2] += m.totals().get(seat);
            }
        }
        return totals;
    }

    @Test
    void current_bot_concedes_fewer_points_than_the_first_one() {
        long[] totals = duel(400);
        assertTrue(totals[0] < totals[1] * 0.9, "current " + totals[0] + " vs baseline " + totals[1]);
    }
}
