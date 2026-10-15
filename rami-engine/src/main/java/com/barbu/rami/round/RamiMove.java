package com.barbu.rami.round;

import java.util.List;
import java.util.Objects;

/** Cards are referenced by id; list order is the order laid on the table. */
public sealed interface RamiMove {

    enum Source {
        STOCK,
        DISCARD
    }

    record Draw(Source source) implements RamiMove {
        public Draw {
            if (source == null) {
                throw new IllegalArgumentException("missing draw source");
            }
        }
    }

    record Open(List<List<Integer>> melds) implements RamiMove {
        public Open {
            if (melds == null || melds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("missing meld");
            }
            melds = melds.stream().map(RamiMove::ids).toList();
        }
    }

    record NewMeld(List<Integer> cards) implements RamiMove {
        public NewMeld {
            cards = ids(cards);
        }
    }

    record LayOff(int meldId, List<Integer> before, List<Integer> after) implements RamiMove {
        public LayOff {
            before = ids(before);
            after = ids(after);
        }
    }

    record SwapJoker(int meldId, int cardId) implements RamiMove {}

    record ResetTurn() implements RamiMove {}

    record Discard(int cardId) implements RamiMove {}

    // Moves arrive from untrusted clients: reject nulls here rather than NPE deep in the engine.
    private static List<Integer> ids(List<Integer> ids) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("missing card id");
        }
        return List.copyOf(ids);
    }
}
