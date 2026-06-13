package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.rating.EloConfig;
import com.barbu.app.room.RankedMatchmaker.Candidate;
import com.barbu.app.room.RankedMatchmaker.Formation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RankedMatchmakerDecisionTest {

    // Table de 4, fenêtre initiale 100 (+50/5s), bot-fill à 20s, seuil 1100.
    private final EloConfig cfg = EloConfig.defaults();

    @Test
    void forms_a_full_table_when_four_close_ratings_are_waiting() {
        List<Candidate> waiting =
                List.of(new Candidate(1000, 0), new Candidate(1050, 0), new Candidate(1080, 0), new Candidate(1020, 0));
        Optional<Formation> f = RankedMatchmaker.decideFormation(waiting, 1000L, cfg);
        assertTrue(f.isPresent());
        assertEquals(4, f.get().indices().size());
        assertEquals(0, f.get().botsToAdd());
    }

    @Test
    void does_not_form_when_spread_exceeds_window() {
        // écart 1000..1400 = 400 > fenêtre 100 à t=0
        List<Candidate> waiting =
                List.of(new Candidate(1000, 0), new Candidate(1100, 0), new Candidate(1250, 0), new Candidate(1400, 0));
        assertTrue(RankedMatchmaker.decideFormation(waiting, 0L, cfg).isEmpty());
    }

    @Test
    void forms_once_the_window_has_widened_enough() {
        List<Candidate> waiting =
                List.of(new Candidate(1000, 0), new Candidate(1100, 0), new Candidate(1250, 0), new Candidate(1400, 0));
        // À t = 45s : fenêtre = 100 + 50*floor(45000/5000) = 100 + 50*9 = 550 >= 400.
        assertTrue(RankedMatchmaker.decideFormation(waiting, 45000L, cfg).isPresent());
    }

    @Test
    void bot_fills_a_low_elo_table_after_the_timeout() {
        List<Candidate> waiting = List.of(new Candidate(1000, 0), new Candidate(1050, 0));
        // t = 21s > 20s, max rating 1050 <= 1100 → 2 humains + 2 bots
        Optional<Formation> f = RankedMatchmaker.decideFormation(waiting, 21000L, cfg);
        assertTrue(f.isPresent());
        assertEquals(2, f.get().indices().size());
        assertEquals(2, f.get().botsToAdd());
    }

    @Test
    void never_bot_fills_a_high_elo_seat() {
        List<Candidate> waiting = List.of(new Candidate(1500, 0), new Candidate(1050, 0));
        // un siège à 1500 > seuil 1100 → pas de bot-fill même après le timeout
        assertTrue(RankedMatchmaker.decideFormation(waiting, 60000L, cfg).isEmpty());
    }

    @Test
    void does_not_bot_fill_before_the_timeout() {
        List<Candidate> waiting = List.of(new Candidate(1000, 0));
        assertTrue(RankedMatchmaker.decideFormation(waiting, 10000L, cfg).isEmpty());
    }
}
