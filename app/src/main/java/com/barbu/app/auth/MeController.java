package com.barbu.app.auth;

import com.barbu.app.persistence.Entities.PlayerRatingEntity;
import com.barbu.app.persistence.Repositories.PlayerRatingRepository;
import com.barbu.app.persistence.Repositories.UserRepository;
import com.barbu.app.rating.EloConfig;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import java.util.Map;

@Controller("/me")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class MeController {

    private final UserRepository users;
    private final PlayerRatingRepository ratings;
    private final EloConfig config;

    public MeController(UserRepository users, PlayerRatingRepository ratings, EloConfig config) {
        this.users = users;
        this.ratings = ratings;
        this.config = config;
    }

    @Get
    public Map<String, Object> me(Authentication authentication) {
        String username = authentication.getName();
        int rating = config.initialRating();
        int gamesPlayed = 0;
        var user = users.findByUsername(username);
        if (user.isPresent()) {
            var row = ratings.findById(user.get().id());
            if (row.isPresent()) {
                PlayerRatingEntity r = row.get();
                rating = r.rating();
                gamesPlayed = r.gamesPlayed();
            }
        }
        return Map.of("username", username, "rating", rating, "gamesPlayed", gamesPlayed);
    }
}
