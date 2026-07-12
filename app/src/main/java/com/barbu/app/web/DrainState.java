package com.barbu.app.web;

import jakarta.inject.Singleton;

/** Bascule d'arrêt gracieux : une fois en drain, la readiness échoue et les leases sont relâchés. */
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
