package com.barbu.app.web;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.Map;

@Controller("/health")
@Secured(SecurityRule.IS_ANONYMOUS)
public class HealthController {

    private final DrainState drain;

    public HealthController(DrainState drain) {
        this.drain = drain;
    }

    @Get
    public HttpResponse<Map<String, String>> health() {
        if (drain.isDraining()) {
            return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DRAINING"));
        }
        return HttpResponse.ok(Map.of("status", "UP"));
    }
}
