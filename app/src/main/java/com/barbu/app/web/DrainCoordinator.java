package com.barbu.app.web;

import com.barbu.app.room.RoomManager;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import jakarta.inject.Singleton;

/**
 * À l'arrêt : bascule la readiness en NotReady (k8s cesse de router) PUIS relâche les leases, pour
 * qu'un survivant réhydrate les tables sans fenêtre « redirect vers pod mort ». Le {@code preStop}
 * (sleep) laisse le load-balancer observer le NotReady avant que ce handler ne s'exécute.
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
