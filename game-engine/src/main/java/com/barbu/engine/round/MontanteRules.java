package com.barbu.engine.round;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Rank;
import com.barbu.engine.card.Suit;
import com.barbu.engine.model.Move;
import com.barbu.engine.model.ScoringConfig;
import com.barbu.engine.model.Seats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MontanteRules {
    private MontanteRules() {}

    private static final Card EIGHT_OF_DIAMONDS = new Card(Suit.DIAMONDS, Rank.EIGHT);

    public static List<Move> legalMoves(MontanteState state, int seat) {
        if (seat != state.currentPlayer() || state.hasFinished(seat)) {
            return List.of();
        }
        if (isBoardEmpty(state.board())) {
            return List.of(new Move.PlayCard(EIGHT_OF_DIAMONDS));
        }
        List<Move> playable = new ArrayList<>();
        for (Card c : state.hands().get(seat)) {
            if (state.board().isPlayable(c)) {
                playable.add(new Move.PlayCard(c));
            }
        }
        if (state.aceFollowUp()) {
            // Bonus d'As : le joueur peut enchaîner une carte ou s'arrêter en passant.
            playable.add(new Move.Pass());
            return List.copyOf(playable);
        }
        return playable.isEmpty() ? List.of(new Move.Pass()) : List.copyOf(playable);
    }

    public static RoundState applyMove(MontanteState state, int seat, Move move) {
        if (seat != state.currentPlayer()) {
            throw new IllegalArgumentException("not seat " + seat + "'s turn");
        }
        if (legalMoves(state, seat).stream().noneMatch(m -> m.equals(move))) {
            throw new IllegalArgumentException("illegal move: " + move);
        }

        List<List<Card>> hands = mutableCopy(state.hands());
        MontanteBoard board = state.board();
        List<Integer> finishing = new ArrayList<>(state.finishingOrder());
        int passStreak;
        boolean aceFollowUp = false;

        if (move instanceof Move.PlayCard play) {
            board = board.place(play.card());
            hands.get(seat).remove(play.card());
            boolean emptied = hands.get(seat).isEmpty();
            if (emptied) {
                finishing.add(seat);
            }
            passStreak = 0;
            // Un As ouvre une enchaîne libre : le joueur garde la main et peut poser autant de
            // cartes qu'il veut, jusqu'à ce qu'il passe ou n'ait plus de coup jouable.
            aceFollowUp = (play.card().rank() == Rank.ACE || state.aceFollowUp())
                    && !emptied
                    && canPlayAny(hands.get(seat), board);
        } else if (state.aceFollowUp()) {
            // Renoncer au bonus d'As ne bloque pas la table : une carte vient d'être posée.
            passStreak = state.passStreak();
        } else {
            passStreak = state.passStreak() + 1;
        }

        int nextPlayer = aceFollowUp ? seat : nextActive(seat, hands);
        return new MontanteState(hands, board, finishing, passStreak, nextPlayer, aceFollowUp);
    }

    public static Map<Integer, Integer> score(MontanteState state) {
        if (!state.isComplete()) {
            throw new IllegalStateException("round not complete");
        }
        int[] ranking = ScoringConfig.montanteRanking(state.playerCount());
        List<Integer> order = state.finalRanking();
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int place = 0; place < order.size(); place++) {
            result.put(order.get(place), ranking[place]);
        }
        return result;
    }

    private static boolean canPlayAny(List<Card> hand, MontanteBoard board) {
        for (Card card : hand) {
            if (board.isPlayable(card)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBoardEmpty(MontanteBoard board) {
        for (Suit suit : Suit.values()) {
            if (board.isOpened(suit)) {
                return false;
            }
        }
        return true;
    }

    private static int nextActive(int seat, List<List<Card>> hands) {
        int n = hands.size();
        int next = Seats.next(seat, n);
        for (int i = 0; i < n; i++) {
            if (!hands.get(next).isEmpty()) {
                return next;
            }
            next = Seats.next(next, n);
        }
        return next;
    }

    private static List<List<Card>> mutableCopy(List<List<Card>> in) {
        List<List<Card>> out = new ArrayList<>(in.size());
        for (List<Card> hand : in) {
            out.add(new ArrayList<>(hand));
        }
        return out;
    }
}
