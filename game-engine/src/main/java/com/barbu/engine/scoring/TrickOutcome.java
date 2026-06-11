package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import java.util.List;

/** Contract-agnostic result of a finished (or in-progress) trick-taking round. */
public record TrickOutcome(List<List<Card>> capturedPerSeat, List<Integer> trickTakers, int playerCount) {}
