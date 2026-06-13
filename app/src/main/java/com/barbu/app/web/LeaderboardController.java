package com.barbu.app.web;

import com.barbu.app.rating.RatingService;
import com.barbu.app.rating.RatingService.LeaderboardRow;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;

/** Classement public par rating ELO décroissant. */
@Controller("/leaderboard")
@Secured(SecurityRule.IS_ANONYMOUS)
public class LeaderboardController {

    private final RatingService ratings;

    public LeaderboardController(RatingService ratings) {
        this.ratings = ratings;
    }

    @Get
    public List<LeaderboardRow> top(@QueryValue(defaultValue = "50") int limit) {
        return ratings.topPlayers(Math.clamp(limit, 1, 200));
    }
}
