package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import java.util.List;

/** A data-driven penalty rule for a trick-taking contract. */
public interface TrickScoringRule {
    /** Points per seat for the given outcome. */
    int[] score(TrickOutcome outcome);

    /**
     * Points locked in so far, given the cards already {@code captured} and those still to be played
     * in {@code remainingHands}. A normalized contract spreads its points over the round's full set of
     * scoring units (every trick, every penalty card), not only those already captured, so a seat's
     * running score never shrinks as play goes on and equals {@link #score} once the hands are empty.
     * Defaults to {@link #score}: a fixed per-unit barème is already locked in.
     */
    default int[] runningScore(TrickOutcome captured, List<List<Card>> remainingHands) {
        return score(captured);
    }

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
