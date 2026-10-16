package com.barbu.bot.rami;

import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.rami.card.RamiCard;
import com.barbu.rami.meld.MeldRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Greedy, disjoint melds: longest runs, then highest sets, then pairs completed by a joker. */
final class MeldFinder {
    private static final int HIGH_ACE = 14;

    private MeldFinder() {}

    static List<List<RamiCard>> find(List<RamiCard> hand) {
        return find(hand, false);
    }

    /** {@code setsFirst} gives the other decomposition when a card could join a run or a set. */
    static List<List<RamiCard>> find(List<RamiCard> hand, boolean setsFirst) {
        List<RamiCard> pool = new ArrayList<>(hand);
        List<List<RamiCard>> melds = new ArrayList<>();
        if (setsFirst) {
            for (List<RamiCard> m = bestSet(pool); m != null; m = bestSet(pool)) {
                take(pool, melds, m);
            }
        }
        for (List<RamiCard> m = longestRun(pool); m != null; m = longestRun(pool)) {
            take(pool, melds, m);
        }
        for (List<RamiCard> m = bestSet(pool); m != null; m = bestSet(pool)) {
            take(pool, melds, m);
        }
        for (List<RamiCard> m = jokerMeld(pool); m != null; m = jokerMeld(pool)) {
            take(pool, melds, m);
        }
        return melds;
    }

    private static void take(List<RamiCard> pool, List<List<RamiCard>> melds, List<RamiCard> meld) {
        melds.add(meld);
        pool.removeAll(meld);
    }

    private static List<RamiCard> longestRun(List<RamiCard> pool) {
        List<RamiCard> best = null;
        for (Suit suit : Suit.values()) {
            RamiCard[] at = new RamiCard[HIGH_ACE + 1];
            for (RamiCard c : pool) {
                if (c.isJoker() || c.card().suit() != suit) {
                    continue;
                }
                if (c.card().rank() == Rank.ACE) {
                    at[1] = c;
                    at[HIGH_ACE] = c;
                } else {
                    at[c.card().rank().trickStrength()] = c;
                }
            }
            List<RamiCard> span = new ArrayList<>();
            for (int p = 1; p <= HIGH_ACE; p++) {
                if (at[p] != null && !span.contains(at[p])) {
                    span.add(at[p]);
                } else {
                    best = longer(best, span);
                    span = new ArrayList<>();
                    if (at[p] != null) {
                        span.add(at[p]);
                    }
                }
            }
            best = longer(best, span);
        }
        return best;
    }

    private static List<RamiCard> longer(List<RamiCard> best, List<RamiCard> span) {
        return span.size() >= 3 && (best == null || span.size() > best.size()) ? List.copyOf(span) : best;
    }

    // Ranks are visited low to high, so the last complete set found is the most valuable.
    private static List<RamiCard> bestSet(List<RamiCard> pool) {
        List<RamiCard> best = null;
        for (Rank rank : Rank.values()) {
            Map<Suit, RamiCard> bySuit = new EnumMap<>(Suit.class);
            for (RamiCard c : pool) {
                if (!c.isJoker() && c.card().rank() == rank) {
                    bySuit.putIfAbsent(c.card().suit(), c);
                }
            }
            if (bySuit.size() >= 3) {
                best = List.copyOf(bySuit.values());
            }
        }
        return best;
    }

    private static List<RamiCard> jokerMeld(List<RamiCard> pool) {
        RamiCard joker = pool.stream().filter(RamiCard::isJoker).findFirst().orElse(null);
        if (joker == null) {
            return null;
        }
        for (RamiCard a : pool) {
            for (RamiCard b : pool) {
                if (a.isJoker() || b.isJoker() || a.id() == b.id()) {
                    continue;
                }
                for (List<RamiCard> candidate :
                        List.of(List.of(a, b, joker), List.of(a, joker, b), List.of(joker, a, b))) {
                    if (MeldRules.kind(candidate).isPresent()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}
