package com.barbu.app.rating;

import com.barbu.app.persistence.Entities.PlayerRatingEntity;
import com.barbu.app.persistence.Entities.UserEntity;
import com.barbu.app.persistence.Repositories.PlayerRatingRepository;
import com.barbu.app.persistence.Repositories.UserRepository;
import com.barbu.app.rating.EloCalculator.Participant;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Façade entre le calcul ELO pur et la persistance des ratings de compte. */
@Singleton
public class RatingService {

    private final PlayerRatingRepository ratings;
    private final UserRepository users;
    private final EloCalculator calculator;
    private final EloConfig config;

    public RatingService(PlayerRatingRepository ratings, UserRepository users, EloConfig config) {
        this.ratings = ratings;
        this.users = users;
        this.config = config;
        this.calculator = new EloCalculator(config);
    }

    /** Un siège de fin de partie : son rang (1 = premier) et l'identité du compte (null si bot/invité). */
    public record SeatRating(int seat, Long userId, boolean isBot, int placement) {}

    /** Le mouvement de rating d'un siège humain, pour la diffusion fin de partie. */
    public record RatingUpdate(int seat, int before, int after, int delta) {}

    public record LeaderboardRow(int rank, String username, int rating, int gamesPlayed) {}

    public int ratingOf(long userId) {
        return ratings.findById(userId).map(PlayerRatingEntity::rating).orElse(config.initialRating());
    }

    private int gamesOf(long userId) {
        return ratings.findById(userId).map(PlayerRatingEntity::gamesPlayed).orElse(0);
    }

    /**
     * Applique une partie ranked terminée : calcule les deltas (bots inclus dans le calcul avec
     * leur rating de config), n'écrit que les sièges humains (gamesPlayed+1), et renvoie leurs
     * mouvements. Transactionnel : tout ou rien.
     */
    @Transactional
    public List<RatingUpdate> applyRankedResult(List<SeatRating> seats) {
        List<Participant> participants = new ArrayList<>(seats.size());
        for (SeatRating s : seats) {
            int rating = (s.isBot() || s.userId() == null) ? config.botRating() : ratingOf(s.userId());
            int games = (s.isBot() || s.userId() == null) ? config.provisionalGames() : gamesOf(s.userId());
            participants.add(new Participant(rating, games, s.placement()));
        }
        List<EloCalculator.Delta> deltas = calculator.compute(participants);

        List<RatingUpdate> updates = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < seats.size(); i++) {
            SeatRating s = seats.get(i);
            if (s.isBot() || s.userId() == null) {
                continue;
            }
            int before = participants.get(i).rating();
            int delta = deltas.get(i).ratingDelta();
            int after = before + delta;
            upsert(s.userId(), after, gamesOf(s.userId()) + 1, now);
            updates.add(new RatingUpdate(s.seat(), before, after, delta));
        }
        return updates;
    }

    public List<LeaderboardRow> topPlayers(int limit) {
        List<PlayerRatingEntity> rows = ratings.findAllOrderByRatingDesc(Pageable.from(0, limit));
        List<LeaderboardRow> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            PlayerRatingEntity r = rows.get(i);
            Optional<UserEntity> user = users.findById(r.userId());
            out.add(new LeaderboardRow(i + 1, user.map(UserEntity::username).orElse("?"), r.rating(), r.gamesPlayed()));
        }
        return out;
    }

    /** Upsert portable H2/Postgres (pas de {@code ON CONFLICT}) : existence puis update ou save. */
    private void upsert(long userId, int rating, int gamesPlayed, Instant now) {
        PlayerRatingEntity entity = new PlayerRatingEntity(userId, rating, gamesPlayed, now);
        if (ratings.existsById(userId)) {
            ratings.update(entity);
        } else {
            ratings.save(entity);
        }
    }
}
