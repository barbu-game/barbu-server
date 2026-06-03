package com.barbu.engine.round;

import com.barbu.engine.model.Contract;

public sealed interface RoundState permits TrickTakingState, MontanteState {
    Contract contract();

    int currentPlayer();

    int playerCount();

    boolean isComplete();
}
