package com.barbu.rami;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.rami.card.RamiCard;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestCards {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private TestCards() {}

    /** "7H", "10D", "QS", "AC", or "JK" for a joker; every call gets a fresh id. */
    public static RamiCard c(String code) {
        int id = NEXT_ID.getAndIncrement();
        if (code.equals("JK")) {
            return new RamiCard(id, null);
        }
        Suit suit =
                switch (code.charAt(code.length() - 1)) {
                    case 'H' -> Suit.HEARTS;
                    case 'D' -> Suit.DIAMONDS;
                    case 'C' -> Suit.CLUBS;
                    case 'S' -> Suit.SPADES;
                    default -> throw new IllegalArgumentException(code);
                };
        String r = code.substring(0, code.length() - 1);
        Rank rank =
                switch (r) {
                    case "A" -> Rank.ACE;
                    case "K" -> Rank.KING;
                    case "Q" -> Rank.QUEEN;
                    case "J" -> Rank.JACK;
                    default -> Rank.values()[Integer.parseInt(r) - 2];
                };
        return new RamiCard(id, new Card(suit, rank));
    }

    public static List<RamiCard> cs(String... codes) {
        return Arrays.stream(codes).map(TestCards::c).toList();
    }
}
