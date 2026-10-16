package com.barbu.app.protocol;

import com.barbu.app.protocol.RamiView.MeldView;
import com.barbu.app.protocol.RamiView.RamiCardView;
import com.barbu.app.protocol.RamiView.RoundRecap;
import com.barbu.rami.card.RamiCard;
import com.barbu.rami.match.RamiMatch;
import com.barbu.rami.match.RoundSummary;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rami moves from client JSON, and the redacted Rami view; client input is never trusted. */
public final class RamiCodec {
    private RamiCodec() {}

    public static RamiMove parseMove(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("missing move");
        }
        Object kind = map.get("kind");
        return switch (String.valueOf(kind)) {
            case "draw" -> new RamiMove.Draw(source(map.get("source")));
            case "open" -> new RamiMove.Open(groups(map.get("melds")));
            case "meld" -> new RamiMove.NewMeld(ids(map.get("cards")));
            case "layoff" ->
                new RamiMove.LayOff(
                        id(map.get("meldId")), ids(orEmpty(map.get("before"))), ids(orEmpty(map.get("after"))));
            case "swapJoker" -> new RamiMove.SwapJoker(id(map.get("meldId")), id(map.get("cardId")));
            case "reset" -> new RamiMove.ResetTurn();
            case "discard" -> new RamiMove.Discard(id(map.get("cardId")));
            default -> throw new IllegalArgumentException("unknown move kind: " + kind);
        };
    }

    public static RamiView view(RamiMatch match, int seat) {
        RamiRound round = match.round();
        if (round == null) {
            RoundSummary last = match.lastRound();
            RoundRecap recap = last == null ? null : new RoundRecap(last.winner(), last.ramiSec(), last.penalties());
            return new RamiView(null, null, null, null, null, null, null, recap);
        }
        boolean active = round.current() == seat;
        return new RamiView(
                round.phase().name(),
                round.hand(seat).stream().map(RamiCodec::card).toList(),
                round.stock().size(),
                round.discard().isEmpty()
                        ? null
                        : card(round.discard().get(round.discard().size() - 1)),
                round.melds().stream()
                        .map(m -> new MeldView(
                                m.id(),
                                m.owner(),
                                m.kind().name(),
                                m.cards().stream().map(RamiCodec::card).toList()))
                        .toList(),
                round.opened(),
                active ? round.mustPlay().stream().sorted().toList() : List.of(),
                null);
    }

    public static RamiCardView card(RamiCard c) {
        if (c.isJoker()) {
            return new RamiCardView(c.id(), null, null, true);
        }
        return new RamiCardView(c.id(), c.card().suit().name(), c.card().rank().name(), false);
    }

    private static RamiMove.Source source(Object value) {
        if ("STOCK".equals(value)) {
            return RamiMove.Source.STOCK;
        }
        if ("DISCARD".equals(value)) {
            return RamiMove.Source.DISCARD;
        }
        throw new IllegalArgumentException("unknown draw source: " + value);
    }

    private static int id(Object value) {
        if (value instanceof Number n && n.doubleValue() == n.intValue()) {
            return n.intValue();
        }
        throw new IllegalArgumentException("expected an integer id but got: " + value);
    }

    private static List<Integer> ids(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("expected a list of card ids");
        }
        List<Integer> ids = new ArrayList<>(list.size());
        for (Object o : list) {
            ids.add(id(o));
        }
        return ids;
    }

    private static List<List<Integer>> groups(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("expected a list of melds");
        }
        List<List<Integer>> groups = new ArrayList<>(list.size());
        for (Object group : list) {
            groups.add(ids(group));
        }
        return groups;
    }

    private static Object orEmpty(Object value) {
        return value == null ? List.of() : value;
    }
}
