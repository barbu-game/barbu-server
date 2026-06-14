package com.barbu.bot;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import com.barbu.engine.round.MontanteState;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundState;
import com.barbu.engine.round.Trick;
import com.barbu.engine.round.TrickTakingState;
import java.util.Comparator;
import java.util.List;

/**
 * A pragmatic, non-learning strategy: shed dangerous cards while ducking tricks for
 * the negative contracts — but in per-card contracts it grabs a penalty-free trick when
 * it can do so risk-free, dumping a high card rather than hoarding it. Unloads extreme
 * cards first in the montante. Good enough to fill empty seats; not an optimal player.
 */
public final class HeuristicBot implements BotStrategy {

    @Override
    public Move chooseMove(RoundState state, int seat) {
        List<Move> legal = RoundEngine.legalMoves(state, seat);
        if (legal.size() == 1) {
            return legal.getFirst();
        }
        return switch (state) {
            case TrickTakingState t -> chooseTrickMove(t, legal);
            case MontanteState m -> chooseMontanteMove(legal);
        };
    }

    private Move chooseTrickMove(TrickTakingState state, List<Move> legal) {
        Contract contract = state.contract();
        Trick trick = state.currentTrick();
        List<Card> cards = playableCards(legal);
        Suit led = trick.ledSuit();

        if (led == null) {
            Card lead = cards.stream()
                    .min(Comparator.comparingInt((Card c) -> danger(contract, c))
                            .thenComparingInt(c -> c.rank().trickStrength()))
                    .orElseThrow();
            return new Move.PlayCard(lead);
        }

        int winningStrength = currentWinningStrength(trick, led);
        List<Card> losing = cards.stream()
                .filter(c -> c.suit() != led || c.rank().trickStrength() < winningStrength)
                .toList();

        Move freeTake = freeTake(contract, trick, cards, led, winningStrength);
        if (freeTake != null) {
            return freeTake;
        }

        if (!losing.isEmpty()) {
            Card shed = losing.stream()
                    .max(Comparator.comparingInt((Card c) -> danger(contract, c))
                            .thenComparingInt(c -> c.rank().trickStrength()))
                    .orElseThrow();
            return new Move.PlayCard(shed);
        }

        Card lowest = cards.stream()
                .min(Comparator.comparingInt(c -> c.rank().trickStrength()))
                .orElseThrow();
        return new Move.PlayCard(lowest);
    }

    /**
     * In a per-card contract a trick carrying no penalty card is free to take. On the trick's
     * final card we can grab it with our most dangerous high card — shedding a future liability
     * at zero cost. We only do this as the last player so no one can still discard a penalty
     * onto the trick after us; returns {@code null} when taking is unsafe or pointless.
     */
    private static Move freeTake(Contract contract, Trick trick, List<Card> cards, Suit led, int winningStrength) {
        if (!isCardPenaltyContract(contract)
                || trick.cards().size() != trick.playerCount() - 1
                || trick.cards().stream().anyMatch(c -> danger(contract, c) > 0)) {
            return null;
        }
        return cards.stream()
                .filter(c -> c.suit() == led && c.rank().trickStrength() > winningStrength)
                .filter(c -> danger(contract, c) == 0)
                .max(Comparator.comparingInt(c -> c.rank().trickStrength()))
                .map(c -> (Move) new Move.PlayCard(c))
                .orElse(null);
    }

    private static boolean isCardPenaltyContract(Contract contract) {
        return switch (contract) {
            case NO_HEARTS, NO_QUEENS, NO_RED_KINGS, NO_KING_OF_HEARTS, NO_JACKS -> true;
            default -> false;
        };
    }

    private Move chooseMontanteMove(List<Move> legal) {
        List<Card> cards = playableCards(legal);
        if (cards.isEmpty()) {
            return legal.getFirst();
        }
        Card extreme = cards.stream()
                .max(Comparator.comparingInt(c -> Math.abs(c.rank().montanteValue() - 8)))
                .orElseThrow();
        return new Move.PlayCard(extreme);
    }

    private static int currentWinningStrength(Trick trick, Suit led) {
        int best = -1;
        for (Card c : trick.cards()) {
            if (c.suit() == led) {
                best = Math.max(best, c.rank().trickStrength());
            }
        }
        return best;
    }

    private static int danger(Contract contract, Card card) {
        return switch (contract) {
            case NO_HEARTS -> card.isHeart() ? 1 : 0;
            case NO_QUEENS -> card.isQueen() ? 1 : 0;
            case NO_RED_KINGS -> card.isRedKing() ? 1 : 0;
            case NO_KING_OF_HEARTS -> card.isKingOfHearts() ? 1 : 0;
            case NO_JACKS -> card.isJack() ? 1 : 0;
            default -> 0;
        };
    }

    private static List<Card> playableCards(List<Move> legal) {
        return legal.stream()
                .filter(m -> m instanceof Move.PlayCard)
                .map(m -> ((Move.PlayCard) m).card())
                .toList();
    }
}
