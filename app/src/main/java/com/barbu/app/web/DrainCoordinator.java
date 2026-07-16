package com.barbu.app.web;

import com.barbu.app.room.RoomManager;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import jakarta.inject.Singleton;

/**
 * On shutdown: flips readiness to NotReady (k8s stops routing) THEN releases the leases, so that a
 * survivor rehydrates the tables without a "redirect to dead pod" window. The {@code preStop}
 * (sleep) lets the load-balancer observe the NotReady before this handler runs.
 */
@Singleton
public class DrainCoordinator implements ApplicationEventListener<ServerShutdownEvent> {

    private final DrainState drain;
    private final RoomManager rooms;

    public DrainCoordinator(DrainState drain, RoomManager rooms) {
        this.drain = drain;
        this.rooms = rooms;
    }

    @Override
    public void onApplicationEvent(ServerShutdownEvent event) {
        drain.startDraining();
        rooms.releaseAllLeases();
    }
}
