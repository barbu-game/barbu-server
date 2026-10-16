package com.barbu.bot.rami;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.meld.Meld;
import com.barbu.rami.meld.MeldRules;
import com.barbu.rami.round.Phase;
import com.barbu.rami.round.RamiMove;
import com.barbu.rami.round.RamiRound;
import com.barbu.rami.round.RamiRoundEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class RamiBot {

    public RamiMove chooseMove(RamiRound round, int seat) {
        List<RamiCard> hand = round.hand(seat);
        boolean opened = round.opened().get(seat);
        if (round.phase() == Phase.DRAW) {
            boolean takeDiscard = opened ? discardFits(round, hand) : discardOpens(round, hand);
            return new RamiMove.Draw(takeDiscard ? RamiMove.Source.DISCARD : RamiMove.Source.STOCK);
        }
        if (opened && !round.mustPlay().isEmpty()) {
            List<RamiCard> pending =
                    hand.stream().filter(c -> round.mustPlay().contains(c.id())).toList();
            Optional<RamiMove> lay = layOff(round, hand, pending);
            if (lay.isPresent()) {
                return lay.get();
            }
            for (List<RamiCard> meld : MeldFinder.find(hand)) {
                if (meld.size() < hand.size() && meld.stream().anyMatch(pending::contains)) {
                    return new RamiMove.NewMeld(ids(meld));
                }
            }
            return new RamiMove.ResetTurn();
        }
        List<List<RamiCard>> melds = MeldFinder.find(hand);
        if (!opened) {
            List<List<RamiCard>> opening = bestOpening(hand);
            Set<Integer> laid =
                    opening.stream().flatMap(List::stream).map(RamiCard::id).collect(Collectors.toSet());
            if (!opening.isEmpty() && laid.containsAll(round.mustPlay())) {
                return new RamiMove.Open(opening.stream().map(RamiBot::ids).toList());
            }
            if (!round.mustPlay().isEmpty()) {
                return new RamiMove.ResetTurn();
            }
        } else {
            for (List<RamiCard> meld : melds) {
                if (meld.size() < hand.size()) {
                    return new RamiMove.NewMeld(ids(meld));
                }
            }
            Optional<RamiMove> lay = layOff(round, hand, hand);
            if (lay.isPresent()) {
                return lay.get();
            }
            Optional<RamiMove> swap = jokerSwap(round, hand);
            if (swap.isPresent()) {
                return swap.get();
            }
        }
        return new RamiMove.Discard(worstCard(hand, melds, opened).id());
    }

    // Only take a joker back when another meld can take it right away: it must be replayed this turn.
    private static Optional<RamiMove> jokerSwap(RamiRound round, List<RamiCard> hand) {
        if (hand.size() < 2) {
            return Optional.empty(); // the joker could not be laid off without emptying the hand
        }
        for (Meld meld : round.melds()) {
            int at = -1;
            for (int i = 0; i < meld.cards().size(); i++) {
                if (meld.cards().get(i).isJoker()) {
                    at = i;
                }
            }
            if (at < 0) {
                continue;
            }
            RamiCard joker = meld.cards().get(at);
            boolean reusable = round.melds().stream().anyMatch(m -> m.id() != meld.id() && extension(m, joker) != null);
            if (!reusable) {
                continue;
            }
            for (RamiCard card : hand) {
                if (card.isJoker()) {
                    continue;
                }
                List<RamiCard> swapped = new ArrayList<>(meld.cards());
                swapped.set(at, card);
                if (MeldRules.kind(swapped).orElse(null) == meld.kind()) {
                    return Optional.of(new RamiMove.SwapJoker(meld.id(), card.id()));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean discardFits(RamiRound round, List<RamiCard> hand) {
        if (hand.size() <= 2 || round.discard().isEmpty()) {
            return false;
        }
        RamiCard top = round.discard().get(round.discard().size() - 1);
        return round.melds().stream().anyMatch(m -> extension(m, top) != null);
    }

    private static boolean discardOpens(RamiRound round, List<RamiCard> hand) {
        if (hand.size() <= 2 || round.discard().isEmpty()) {
            return false;
        }
        RamiCard top = round.discard().get(round.discard().size() - 1);
        List<RamiCard> withTop = new ArrayList<>(hand);
        withTop.add(top);
        return bestOpening(withTop).stream().anyMatch(m -> m.contains(top));
    }

    private static RamiMove.LayOff extension(Meld meld, RamiCard card) {
        List<RamiCard> after = new ArrayList<>(meld.cards());
        after.add(card);
        if (MeldRules.kind(after).orElse(null) == meld.kind()) {
            return new RamiMove.LayOff(meld.id(), List.of(), List.of(card.id()));
        }
        List<RamiCard> before = new ArrayList<>(List.of(card));
        before.addAll(meld.cards());
        if (MeldRules.kind(before).orElse(null) == meld.kind()) {
            return new RamiMove.LayOff(meld.id(), List.of(card.id()), List.of());
        }
        return null;
    }

    private static Optional<RamiMove> layOff(RamiRound round, List<RamiCard> hand, List<RamiCard> candidates) {
        if (hand.size() < 2) {
            return Optional.empty();
        }
        for (RamiCard card : candidates) {
            for (Meld meld : round.melds()) {
                RamiMove.LayOff move = extension(meld, card);
                if (move != null) {
                    return Optional.of(move);
                }
            }
        }
        return Optional.empty();
    }

    /** Highest-scoring set of disjoint melds worth 51+ that keeps a card, over both decompositions. */
    static List<List<RamiCard>> bestOpening(List<RamiCard> hand) {
        List<List<RamiCard>> best = List.of();
        int bestPoints = RamiRoundEngine.OPENING_MIN - 1;
        for (boolean setsFirst : new boolean[] {false, true}) {
            List<List<RamiCard>> melds = MeldFinder.find(hand, setsFirst);
            // At most a handful of melds fit in 14 cards, so every subset is cheap to try.
            for (int mask = 1; mask < (1 << melds.size()); mask++) {
                List<List<RamiCard>> chosen = new ArrayList<>();
                int used = 0;
                int points = 0;
                for (int k = 0; k < melds.size(); k++) {
                    if ((mask & (1 << k)) != 0) {
                        chosen.add(melds.get(k));
                        used += melds.get(k).size();
                        points += MeldRules.points(melds.get(k));
                    }
                }
                if (used < hand.size() && points > bestPoints) {
                    best = chosen;
                    bestPoints = points;
                }
            }
        }
        return best;
    }

    // Before opening, high cards are what reaches 51; after, they are what costs penalties.
    // With three cards or fewer a pair can never become a meld (it would empty the hand).
    private static RamiCard worstCard(List<RamiCard> hand, List<List<RamiCard>> melds, boolean opened) {
        Set<RamiCard> inMeld = melds.stream().flatMap(List::stream).collect(Collectors.toSet());
        Comparator<RamiCard> byPenalty = opened
                ? Comparator.comparingInt(RamiCard::penalty)
                : Comparator.comparingInt((RamiCard c) -> -c.penalty());
        return hand.stream()
                .filter(c -> !c.isJoker() && !inMeld.contains(c))
                .max(Comparator.comparing((RamiCard c) -> (opened && hand.size() <= 3) || !hasNeighbour(c, hand))
                        .thenComparing(byPenalty))
                .or(() -> hand.stream().filter(c -> !c.isJoker()).max(byPenalty))
                .orElse(hand.get(0));
    }

    /** Same rank, or same suit within two ranks, excluding an identical copy: worth keeping for a meld. */
    private static boolean hasNeighbour(RamiCard card, List<RamiCard> hand) {
        return hand.stream()
                .anyMatch(o -> o.id() != card.id()
                        && !o.isJoker()
                        && !o.card().equals(card.card())
                        && (o.card().rank() == card.card().rank()
                                || (o.card().suit() == card.card().suit()
                                        && Math.abs(o.card().rank().ordinal()
                                                        - card.card().rank().ordinal())
                                                <= 2)));
    }

    private static List<Integer> ids(List<RamiCard> cards) {
        return cards.stream().map(RamiCard::id).toList();
    }
}
