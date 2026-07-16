package com.barbu.app.web;

import jakarta.inject.Singleton;

/** Graceful shutdown switch: once draining, readiness fails and the leases are released. */
@Singleton
public class DrainState {

    private volatile boolean draining;

    public boolean isDraining() {
        return draining;
    }

    public void startDraining() {
        this.draining = true;
    }
}
