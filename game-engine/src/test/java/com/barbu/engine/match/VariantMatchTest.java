package com.barbu.engine.match;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.barbu.engine.model.Contract;
import com.barbu.engine.variant.Variant;
import com.barbu.engine.variant.Variants;
import java.util.List;
import org.junit.jupiter.api.Test;

class VariantMatchTest {
    @Test
    void each_variant_plays_a_full_match_in_imposed_order() {
        for (Variant v : Variants.all()) {
            for (int n = 2; n <= 5; n++) {
                MatchState m = MatchEngine.newMatch(n, 7L, v);
                assertEquals(v.contracts().size() * n, m.plannedRounds(), v.id() + " n=" + n);
                while (!MatchEngine.isComplete(m)) {
                    m = MatchEngine.playOut(MatchEngine.startNextContract(m));
                }
                assertEquals(v.contracts().size() * n, m.roundNumber(), v.id() + " n=" + n);
                assertEquals(v.contracts().size() * n, m.history().size(), v.id() + " n=" + n);
            }
        }
    }

    @Test
    void salad_equals_the_sum_of_its_component_contracts_on_the_same_deal() {
        List<Contract> parts = List.of(
                Contract.NO_TRICKS,
                Contract.NO_HEARTS,
                Contract.NO_QUEENS,
                Contract.NO_KING_OF_HEARTS,
                Contract.NO_LAST_TWO_TRICKS);
        int[] sum = new int[4];
        for (Contract c : parts) {
            MatchState m =
                    MatchEngine.playOut(MatchEngine.chooseContract(MatchEngine.newMatch(4, 99L, Variants.CLASSIC), c));
            int[] pts = m.history().get(0).points();
            for (int s = 0; s < 4; s++) {
                sum[s] += pts[s];
            }
        }
        MatchState salad = MatchEngine.playOut(
                MatchEngine.chooseContract(MatchEngine.newMatch(4, 99L, Variants.CLASSIC), Contract.SALADE));
        int[] saladPts = salad.history().get(0).points();
        for (int s = 0; s < 4; s++) {
            assertEquals(sum[s], saladPts[s], "seat " + s);
        }
    }
}
