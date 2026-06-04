package com.barbu.app.protocol;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.MontanteBoard;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates engine value types to and from the JSON wire shapes used by clients. */
public final class Codec {
    private Codec() {
    }

    public static Map<String, Object> cardToMap(Card card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("suit", card.suit().name());
        map.put("rank", card.rank().name());
        return map;
    }

    public static Card parseCard(Map<String, Object> map) {
        return new Card(Suit.valueOf((String) map.get("suit")), Rank.valueOf((String) map.get("rank")));
    }

    public static Map<String, Object> moveToMap(Move move) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (move instanceof Move.PlayCard play) {
            map.put("kind", "card");
            map.put("suit", play.card().suit().name());
            map.put("rank", play.card().rank().name());
        } else {
            map.put("kind", "pass");
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Move parseMove(Map<String, Object> map) {
        String kind = (String) map.get("kind");
        if ("pass".equals(kind)) {
            return new Move.Pass();
        }
        return new Move.PlayCard(parseCard((Map<String, Object>) (Map<?, ?>) map));
    }

    public static Map<String, Object> boardToMap(MontanteBoard board) {
        Map<String, Object> columns = new LinkedHashMap<>();
        for (Suit suit : Suit.values()) {
            Map<String, Object> col = new LinkedHashMap<>();
            boolean opened = board.isOpened(suit);
            col.put("opened", opened);
            col.put("low", opened ? board.low()[suit.ordinal()] : 0);
            col.put("high", opened ? board.high()[suit.ordinal()] : 0);
            columns.put(suit.name(), col);
        }
        return columns;
    }
}
