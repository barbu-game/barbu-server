package com.barbu.engine.scoring;

/** A data-driven penalty rule for a trick-taking contract. */
public interface TrickScoringRule {
    /** Points per seat for the given outcome. */
    int[] score(TrickOutcome outcome);

    /** Human-readable rule, the single source of the text shown to players. */
    String describe();
}
