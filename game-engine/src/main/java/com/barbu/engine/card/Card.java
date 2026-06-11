package com.barbu.engine.card;

public record Card(Suit suit, Rank rank) {
    public boolean isRed() {
        return suit.isRed();
    }

    public boolean isHeart() {
        return suit == Suit.HEARTS;
    }

    public boolean isQueen() {
        return rank == Rank.QUEEN;
    }

    public boolean isRedKing() {
        return rank == Rank.KING && suit.isRed();
    }

    public boolean isKingOfHearts() {
        return rank == Rank.KING && suit == Suit.HEARTS;
    }

    public boolean isJack() {
        return rank == Rank.JACK;
    }
}
