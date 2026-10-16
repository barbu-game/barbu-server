package com.barbu.app.rating;

import com.barbu.app.persistence.Entities.PlayerRatingEntity;
import com.barbu.app.persistence.Entities.RamiRatingEntity;
import com.barbu.app.persistence.Entities.UserEntity;
import com.barbu.app.persistence.Repositories.PlayerRatingRepository;
import com.barbu.app.persistence.Repositories.RamiRatingRepository;
import com.barbu.app.persistence.Repositories.UserRepository;
import com.barbu.app.rating.EloCalculator.Participant;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Facade between the pure ELO computation and the persistence of account ratings. Each game keeps its
 * own ladder: Barbu in {@code player_ratings}, Rami in {@code rami_ratings}.
 */
@Singleton
public class RatingService {

    private static final String RAMI = "RAMI";

    private final PlayerRatingRepository ratings;
    private final RamiRatingRepository ramiRatings;
    private final UserRepository users;
    private final EloCalculator calculator;
    private final EloConfig config;

    public RatingService(
            PlayerRatingRepository ratings, RamiRatingRepository ramiRatings, UserRepository users, EloConfig config) {
        this.ratings = ratings;
        this.ramiRatings = ramiRatings;
        this.users = users;
        this.config = config;
        this.calculator = new EloCalculator(config);
    }

    /** An end-of-game seat: its placement (1 = first) and the account identity (null if bot/guest). */
    public record SeatRating(int seat, Long userId, boolean isBot, int placement) {}

    /** The rating movement of a human seat, for the end-of-game broadcast. */
    public record RatingUpdate(int seat, int before, int after, int delta) {}

    public record LeaderboardRow(int rank, String username, int rating, int gamesPlayed) {}

    private record Row(long userId, int rating, int gamesPlayed) {}

    public int ratingOf(long userId) {
        return ratingOf(userId, "BARBU");
    }

    public int ratingOf(long userId, String gameType) {
        return row(userId, gameType).map(Row::rating).orElse(config.initialRating());
    }

    private int gamesOf(long userId, String gameType) {
        return row(userId, gameType).map(Row::gamesPlayed).orElse(0);
    }

    private Optional<Row> row(long userId, String gameType) {
        return RAMI.equalsIgnoreCase(gameType)
                ? ramiRatings.findById(userId).map(r -> new Row(r.userId(), r.rating(), r.gamesPlayed()))
                : ratings.findById(userId).map(r -> new Row(r.userId(), r.rating(), r.gamesPlayed()));
    }

    @Transactional
    public List<RatingUpdate> applyRankedResult(List<SeatRating> seats) {
        return applyRankedResult("BARBU", seats);
    }

    /**
     * Applies a finished ranked game: computes the deltas (bots included in the computation with
     * their config rating), writes only the human seats (gamesPlayed+1), and returns their
     * movements. Transactional: all or nothing.
     */
    @Transactional
    public List<RatingUpdate> applyRankedResult(String gameType, List<SeatRating> seats) {
        List<Participant> participants = new ArrayList<>(seats.size());
        for (SeatRating s : seats) {
            boolean unrated = s.isBot() || s.userId() == null;
            int rating = unrated ? config.botRating() : ratingOf(s.userId(), gameType);
            int games = unrated ? config.provisionalGames() : gamesOf(s.userId(), gameType);
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
            upsert(gameType, s.userId(), after, gamesOf(s.userId(), gameType) + 1, now);
            updates.add(new RatingUpdate(s.seat(), before, after, delta));
        }
        return updates;
    }

    public List<LeaderboardRow> topPlayers(int limit) {
        return topPlayers("BARBU", limit);
    }

    public List<LeaderboardRow> topPlayers(String gameType, int limit) {
        Pageable page = Pageable.from(0, limit);
        List<Row> rows = RAMI.equalsIgnoreCase(gameType)
                ? ramiRatings.findAllOrderByRatingDesc(page).stream()
                        .map(r -> new Row(r.userId(), r.rating(), r.gamesPlayed()))
                        .toList()
                : ratings.findAllOrderByRatingDesc(page).stream()
                        .map(r -> new Row(r.userId(), r.rating(), r.gamesPlayed()))
                        .toList();
        List<LeaderboardRow> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Optional<UserEntity> user = users.findById(r.userId());
            out.add(new LeaderboardRow(i + 1, user.map(UserEntity::username).orElse("?"), r.rating(), r.gamesPlayed()));
        }
        return out;
    }

    /** Portable H2/Postgres upsert (no {@code ON CONFLICT}): existence check then update or save. */
    private void upsert(String gameType, long userId, int rating, int gamesPlayed, Instant now) {
        if (RAMI.equalsIgnoreCase(gameType)) {
            RamiRatingEntity entity = new RamiRatingEntity(userId, rating, gamesPlayed, now);
            if (ramiRatings.existsById(userId)) {
                ramiRatings.update(entity);
            } else {
                ramiRatings.save(entity);
            }
            return;
        }
        PlayerRatingEntity entity = new PlayerRatingEntity(userId, rating, gamesPlayed, now);
        if (ratings.existsById(userId)) {
            ratings.update(entity);
        } else {
            ratings.save(entity);
        }
    }
}
