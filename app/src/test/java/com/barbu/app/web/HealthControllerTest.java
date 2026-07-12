package com.barbu.app.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void reports_up_then_503_once_draining() {
        DrainState drain = new DrainState();
        HealthController health = new HealthController(drain);

        assertEquals(HttpStatus.OK, health.health().status());

        drain.startDraining();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, health.health().status());
    }
}
