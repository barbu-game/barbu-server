package com.barbu.engine.model;

import com.barbu.engine.card.Card;

public sealed interface Move permits Move.PlayCard, Move.Pass {
    record PlayCard(Card card) implements Move {
    }

    record Pass() implements Move {
    }
}
