package com.barbu.rami.meld;

import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.rami.card.RamiCard;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class MeldRules {
    private static final int LOW_ACE = 1;
    private static final int HIGH_ACE = 14;
    // An ace may open or close a run, never both: A..K or 2..A at most.
    private static final int MAX_RUN = 13;

    private MeldRules() {}

    public static Optional<MeldKind> kind(List<RamiCard> cards) {
        if (isSet(cards)) {
            return Optional.of(MeldKind.SET);
        }
        return runStart(cards).isPresent() ? Optional.of(MeldKind.RUN) : Optional.empty();
    }

    /** Opening value; a joker is worth the card it stands for. */
    public static int points(List<RamiCard> cards) {
        if (isSet(cards)) {
            return cards.size() * RamiCard.rankPoints(reals(cards).get(0).card().rank());
        }
        int start = runStart(cards).orElseThrow(() -> new IllegalArgumentException("not a meld"));
        int sum = 0;
        for (int k = 0; k < cards.size(); k++) {
            sum += positionPoints(start + k);
        }
        return sum;
    }

    // >= 3 cards with at most one joker also guarantees at least two real cards.
    private static boolean jokerBudgetOk(List<RamiCard> cards) {
        return cards.size() >= 3 && cards.stream().filter(RamiCard::isJoker).count() <= 1;
    }

    private static boolean isSet(List<RamiCard> cards) {
        if (!jokerBudgetOk(cards) || cards.size() > 4) {
            return false;
        }
        List<RamiCard> reals = reals(cards);
        Rank rank = reals.get(0).card().rank();
        return reals.stream().allMatch(c -> c.card().rank() == rank)
                && reals.stream().map(c -> c.card().suit()).distinct().count() == reals.size();
    }

    private static OptionalInt runStart(List<RamiCard> cards) {
        if (!jokerBudgetOk(cards) || cards.size() > MAX_RUN) {
            return OptionalInt.empty();
        }
        List<RamiCard> reals = reals(cards);
        Suit suit = reals.get(0).card().suit();
        if (reals.stream().anyMatch(c -> c.card().suit() != suit)) {
            return OptionalInt.empty();
        }
        int i = cards.get(0).isJoker() ? 1 : 0;
        Rank first = cards.get(i).card().rank();
        int[] candidates =
                first == Rank.ACE ? new int[] {LOW_ACE - i, HIGH_ACE - i} : new int[] {first.trickStrength() - i};
        for (int start : candidates) {
            if (fitsRun(cards, start)) {
                return OptionalInt.of(start);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean fitsRun(List<RamiCard> cards, int start) {
        if (start < LOW_ACE || start + cards.size() - 1 > HIGH_ACE) {
            return false;
        }
        for (int k = 0; k < cards.size(); k++) {
            RamiCard c = cards.get(k);
            if (!c.isJoker() && !matches(c.card().rank(), start + k)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Rank rank, int position) {
        return rank == Rank.ACE ? position == LOW_ACE || position == HIGH_ACE : rank.trickStrength() == position;
    }

    private static int positionPoints(int position) {
        if (position == LOW_ACE) {
            return 1;
        }
        return position == HIGH_ACE ? 11 : Math.min(position, 10);
    }

    private static List<RamiCard> reals(List<RamiCard> cards) {
        return cards.stream().filter(c -> !c.isJoker()).toList();
    }
}
