package com.barbu.rami.match;

import java.util.List;

/** {@code winner} is null when the round ran out of cards. */
public record RoundSummary(Integer winner, boolean ramiSec, List<Integer> penalties) {}
