package com.barbu.engine.scoring;

import com.barbu.engine.card.Card;
import java.util.List;

/** A data-driven penalty rule for a trick-taking contract. */
public interface TrickScoringRule {
    /** Points per seat for the given outcome. */
    int[] score(TrickOutcome outcome);

    /**
     * Points locked in so far, given the cards already {@code captured} and those still to be played in
     * {@code remainingHands}. Invariant (verified by {@code RunningScoreInvariantsTest}): a seat's share
     * never shrinks in magnitude as play goes on (V1), and equals {@link #score} once the hands are empty
     * (V2); the result is a pure function of its inputs (V3). A rule must only count what can no longer
     * change — a fixed per-unit scale is already locked, hence the default delegates to {@link #score}.
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
