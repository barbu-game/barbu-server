package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.Move;
import com.barbu.engine.model.ScoringConfig;
import com.barbu.engine.model.Seats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrickTakingRules {
    private TrickTakingRules() {
    }

    public static List<Move> legalMoves(TrickTakingState state, int seat) {
        if (seat != state.currentPlayer()) {
            return List.of();
        }
        List<Card> hand = state.hands().get(seat);
        Trick trick = state.currentTrick();
        // A completed trick is kept on the table for display; the winner now leads a new one.
        Suit led = trick.isComplete() ? null : trick.ledSuit();
        List<Card> playable = new ArrayList<>();
        if (led != null) {
            for (Card c : hand) {
                if (c.suit() == led) {
                    playable.add(c);
                }
            }
        }
        if (playable.isEmpty()) {
            playable = hand;
        }
        List<Move> moves = new ArrayList<>(playable.size());
        for (Card c : playable) {
            moves.add(new Move.PlayCard(c));
        }
        return List.copyOf(moves);
    }

    public static RoundState applyMove(TrickTakingState state, int seat, Move move) {
        if (seat != state.currentPlayer()) {
            throw new IllegalArgumentException("not seat " + seat + "'s turn");
        }
        if (!(move instanceof Move.PlayCard play)) {
            throw new IllegalArgumentException("trick-taking accepts only PlayCard");
        }
        if (legalMoves(state, seat).stream().noneMatch(m -> m.equals(move))) {
            throw new IllegalArgumentException("illegal move: " + play.card());
        }

        List<List<Card>> hands = mutableCopy(state.hands());
        hands.get(seat).remove(play.card());

        // The winner leads the next trick: collect the displayed trick before adding the card.
        Trick current = state.currentTrick();
        if (current.isComplete()) {
            current = Trick.startedBy(seat, state.playerCount());
        }
        Trick trick = current.withCard(play.card());
        List<List<Card>> captured = mutableCopy(state.captured());

        if (trick.isComplete()) {
            int taker = trick.taker();
            captured.get(taker).addAll(trick.cards());
            // Keep the completed trick on the table; the taker is the next to act.
            return new TrickTakingState(state.contract(), hands, trick, captured, taker);
        }
        return new TrickTakingState(state.contract(), hands, trick, captured,
                Seats.next(seat, state.playerCount()));
    }

    /** Clear a finished trick (already captured) so the taker can lead a fresh one. */
    public static RoundState collectTrick(TrickTakingState state) {
        Trick trick = state.currentTrick();
        if (!trick.isComplete()) {
            return state;
        }
        int taker = trick.taker();
        return new TrickTakingState(state.contract(), state.hands(),
                Trick.startedBy(taker, state.playerCount()), state.captured(), taker);
    }

    public static Map<Integer, Integer> score(TrickTakingState state) {
        if (!state.isComplete()) {
            throw new IllegalStateException("round not complete");
        }
        return runningScores(state);
    }

    /** Per-seat penalty for what has been captured so far (valid mid-round). */
    public static Map<Integer, Integer> runningScores(TrickTakingState state) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int seat = 0; seat < state.playerCount(); seat++) {
            result.put(seat, scoreSeat(state.contract(), state.captured().get(seat), state.playerCount()));
        }
        return result;
    }

    private static int scoreSeat(Contract contract, List<Card> captured, int playerCount) {
        return switch (contract) {
            case NO_TRICKS -> (captured.size() / playerCount) * ScoringConfig.PER_TRICK;
            case NO_HEARTS -> (int) captured.stream().filter(Card::isHeart).count() * ScoringConfig.PER_HEART;
            case NO_QUEENS -> (int) captured.stream().filter(Card::isQueen).count() * ScoringConfig.PER_QUEEN;
            case NO_RED_KINGS -> (int) captured.stream().filter(Card::isRedKing).count() * ScoringConfig.PER_RED_KING;
            case MONTANTE -> throw new IllegalArgumentException("montante is not a trick-taking contract");
        };
    }

    private static List<List<Card>> mutableCopy(List<List<Card>> in) {
        List<List<Card>> out = new ArrayList<>(in.size());
        for (List<Card> hand : in) {
            out.add(new ArrayList<>(hand));
        }
        return out;
    }
}
