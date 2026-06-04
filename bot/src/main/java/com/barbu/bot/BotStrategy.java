package com.barbu.bot;

import com.barbu.engine.match.MatchState;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.RoundState;

public interface BotStrategy {

    /** Pick a legal move for {@code seat} in the current round. */
    Move chooseMove(RoundState state, int seat);

    /** Pick a contract the current dealer has not played yet. */
    Contract chooseContract(MatchState state);
}
