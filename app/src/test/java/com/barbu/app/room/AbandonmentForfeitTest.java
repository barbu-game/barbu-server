package com.barbu.app.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.barbu.app.persistence.Entities.UserEntity;
import com.barbu.app.persistence.Repositories.PlayerRatingRepository;
import com.barbu.app.persistence.Repositories.UserRepository;
import com.barbu.app.rating.RatingService;
import com.barbu.engine.variant.Variants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

@MicronautTest(transactional = false)
class AbandonmentForfeitTest {

    @Inject
    RatingService ratings;

    @Inject
    UserRepository users;

    @Inject
    PlayerRatingRepository ratingRepo;

    @Test
    void cancelling_a_ranked_game_because_the_only_human_left_drops_their_rating() {
        long userId = users.save(new UserEntity(null, "deserter-" + System.nanoTime(), "x", Instant.now()))
                .id();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        try {
            GameRoom room = new GameRoom(
                    "T",
                    4,
                    Variants.DEVELOPER,
                    new ObjectMapper(),
                    scheduler,
                    0,
                    null,
                    null,
                    "ranked",
                    ratings,
                    null,
                    60000,
                    2);
            int seat = room.addHuman(new FakeSession(), "Deserter", userId);
            room.addBot();
            room.addBot();
            room.addBot();
            room.start(7L);

            // L'unique humain se déconnecte : la table n'a plus aucun humain.
            room.handleDisconnect(seat);
            assertTrue(room.isEmptyOfHumans());
            room.recordAbandonmentForfeit();

            var rating = ratingRepo.findById(userId).orElseThrow();
            assertEquals(1, rating.gamesPlayed(), "the forfeit counts as a played ranked game");
            assertTrue(rating.rating() < 1000, "leaving a ranked game must lose elo (forced last vs the bots)");
        } finally {
            scheduler.shutdownNow();
        }
    }
}
