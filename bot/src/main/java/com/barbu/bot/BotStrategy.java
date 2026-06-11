package com.barbu.bot;

import com.barbu.engine.model.Move;
import com.barbu.engine.round.RoundState;

public interface BotStrategy {

    /** Pick a legal move for {@code seat} in the current round. */
    Move chooseMove(RoundState state, int seat);
}
