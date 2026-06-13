package com.barbu.app.rating;

import static org.junit.jupiter.api.Assertions.*;

import com.barbu.app.persistence.Entities.UserEntity;
import com.barbu.app.persistence.Repositories.PlayerRatingRepository;
import com.barbu.app.persistence.Repositories.UserRepository;
import com.barbu.app.rating.RatingService.RatingUpdate;
import com.barbu.app.rating.RatingService.SeatRating;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
class RatingServiceTest {

    @Inject
    RatingService ratings;

    @Inject
    UserRepository users;

    @Inject
    PlayerRatingRepository ratingRepo;

    private long newUser(String name) {
        return users.save(new UserEntity(null, name, "x", Instant.now())).id();
    }

    @Test
    void unrated_user_reads_initial_rating() {
        long id = newUser("nobody-" + System.nanoTime());
        assertEquals(1000, ratings.ratingOf(id));
    }

    @Test
    void applying_a_ranked_result_writes_humans_and_skips_bots() {
        long winner = newUser("winner-" + System.nanoTime());
        long loser = newUser("loser-" + System.nanoTime());

        List<RatingUpdate> updates = ratings.applyRankedResult(List.of(
                new SeatRating(0, winner, false, 1),
                new SeatRating(1, loser, false, 2),
                new SeatRating(2, null, true, 3),
                new SeatRating(3, null, true, 4)));

        // Deux humains mis à jour, bots ignorés.
        assertEquals(2, updates.size());
        RatingUpdate w = updates.stream().filter(u -> u.seat() == 0).findFirst().orElseThrow();
        RatingUpdate l = updates.stream().filter(u -> u.seat() == 1).findFirst().orElseThrow();
        assertEquals(1000, w.before());
        // 1er place gagne ; il gagne plus que le 2e (face à deux bots faibles, finir 2e peut rester
        // légèrement positif — l'important est l'ordre des mouvements, pas un signe absolu).
        assertTrue(w.delta() > 0, "winner should gain");
        assertTrue(w.delta() > l.delta(), "winner should gain more than runner-up");
        assertEquals(w.before() + w.delta(), w.after());

        // Persistance : ligne créée, gamesPlayed = 1.
        assertEquals(w.after(), ratingRepo.findById(winner).orElseThrow().rating());
        assertEquals(1, ratingRepo.findById(winner).orElseThrow().gamesPlayed());
        assertEquals(1, ratingRepo.findById(loser).orElseThrow().gamesPlayed());
    }

    @Test
    void leaderboard_is_ordered_by_rating_desc() {
        long a = newUser("lead-a-" + System.nanoTime());
        long b = newUser("lead-b-" + System.nanoTime());
        // a bat b → a > 1000 > b
        ratings.applyRankedResult(List.of(new SeatRating(0, a, false, 1), new SeatRating(1, b, false, 2)));
        List<RatingService.LeaderboardRow> top = ratings.topPlayers(50);
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).rating() >= top.get(i).rating());
        }
    }
}
