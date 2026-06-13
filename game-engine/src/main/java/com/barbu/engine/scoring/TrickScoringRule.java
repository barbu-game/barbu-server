package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import java.util.List;

/** A data-driven penalty rule for a trick-taking contract. */
public interface TrickScoringRule {
    /** Points per seat for the given outcome. */
    int[] score(TrickOutcome outcome);

    /** Human-readable rule, the single source of the text shown to players. */
    String describe();

    /**
     * Whether the cards still held in {@code remainingHands} can no longer change any seat's
     * score, so the round may be settled before every card is played. Conservative by default:
     * a rule that cannot prove exhaustion (e.g. per-trick or position-dependent) keeps the round
     * running to the last card.
     */
    default boolean exhausted(List<List<Card>> remainingHands) {
        return false;
    }
}
