package com.barbu.app.protocol;

import com.barbu.app.protocol.GameStateMessage.BoardCell;
import com.barbu.app.protocol.GameStateMessage.CardView;
import com.barbu.app.protocol.GameStateMessage.MoveView;
import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.MontanteBoard;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates engine value types to and from the JSON wire shapes used by clients. */
public final class Codec {
    private Codec() {}

    public static CardView cardView(Card card) {
        return new CardView(card.suit().name(), card.rank().name());
    }

    public static Card parseCard(Map<String, Object> map) {
        return new Card(Suit.valueOf((String) map.get("suit")), Rank.valueOf((String) map.get("rank")));
    }

    public static MoveView moveView(Move move) {
        if (move instanceof Move.PlayCard play) {
            return MoveView.card(play.card().suit().name(), play.card().rank().name());
        }
        return MoveView.pass();
    }

    @SuppressWarnings("unchecked")
    public static Move parseMove(Map<String, Object> map) {
        String kind = (String) map.get("kind");
        if ("pass".equals(kind)) {
            return new Move.Pass();
        }
        return new Move.PlayCard(parseCard((Map<String, Object>) (Map<?, ?>) map));
    }

    public static Map<String, BoardCell> boardView(MontanteBoard board) {
        Map<String, BoardCell> columns = new LinkedHashMap<>();
        for (Suit suit : Suit.values()) {
            boolean opened = board.isOpened(suit);
            int low = opened ? board.low()[suit.ordinal()] : 0;
            int high = opened ? board.high()[suit.ordinal()] : 0;
            columns.put(suit.name(), new BoardCell(opened, low, high));
        }
        return columns;
    }
}
