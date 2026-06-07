package com.barbu.engine.match;

import com.barbu.engine.card.Card;
import com.barbu.engine.card.Deck;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.ContractType;
import com.barbu.engine.model.Move;
import com.barbu.engine.model.Seats;
import com.barbu.engine.round.RoundEngine;
import com.barbu.engine.round.RoundResult;
import com.barbu.engine.round.RoundState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class MatchEngine {
    private MatchEngine() {}

    public static MatchState newMatch(int playerCount, long seed) {
        return newMatch(playerCount, seed, Contract.values().length * playerCount);
    }

    public static MatchState newMatch(int playerCount, long seed, int plannedRounds) {
        if (playerCount < Seats.MIN || playerCount > Seats.MAX) {
            throw new IllegalArgumentException("playerCount out of range: " + playerCount);
        }
        return new MatchState(
                playerCount,
                seed,
                0,
                0,
                plannedRounds,
                EnumSet.noneOf(Contract.class),
                null,
                new int[playerCount],
                List.of());
    }

    public static boolean isComplete(MatchState m) {
        return m.roundNumber() >= m.plannedRounds();
    }

    public static boolean isDealerBoundary(MatchState m) {
        return m.round() == null && m.roundNumber() > 0 && m.playedByDealer().isEmpty() && !isComplete(m);
    }

    /** The contract the current dealer must play next — order is imposed, not chosen. */
    public static Contract nextContract(MatchState m) {
        for (Contract contract : Contract.values()) {
            if (!m.playedByDealer().contains(contract)) {
                return contract;
            }
        }
        throw new IllegalStateException("dealer has already played every contract");
    }

    /** Deal and open the next imposed contract for the current dealer. */
    public static MatchState startNextContract(MatchState m) {
        return chooseContract(m, nextContract(m));
    }

    public static MatchState chooseContract(MatchState m, Contract contract) {
        if (m.round() != null) {
            throw new IllegalStateException("a round is already in progress");
        }
        if (m.playedByDealer().contains(contract)) {
            throw new IllegalArgumentException("dealer already played " + contract);
        }

        long roundSeed = m.seed() * 31 + m.roundNumber();
        List<List<Card>> hands = deal(m.playerCount(), roundSeed);

        RoundState round = contract.type() == ContractType.MONTANTE
                ? RoundEngine.startMontante(hands, RoundEngine.eightOfDiamondsHolder(hands))
                : RoundEngine.startTrickTaking(contract, hands, Seats.next(m.dealer(), m.playerCount()));

        return new MatchState(
                m.playerCount(),
                m.seed(),
                m.dealer(),
                m.roundNumber(),
                m.plannedRounds(),
                m.playedByDealer(),
                round,
                m.totals(),
                m.history());
    }

    public static MatchState applyMove(MatchState m, int seat, Move move) {
        if (m.round() == null) {
            throw new IllegalStateException("no round in progress");
        }
        RoundState round = RoundEngine.applyMove(m.round(), seat, move);
        if (!round.isComplete()) {
            return new MatchState(
                    m.playerCount(),
                    m.seed(),
                    m.dealer(),
                    m.roundNumber(),
                    m.plannedRounds(),
                    m.playedByDealer(),
                    round,
                    m.totals(),
                    m.history());
        }
        return settleRound(m, round);
    }

    /** Apply a move without settling a finished round, so callers can pause first. */
    public static MatchState applyMoveNoSettle(MatchState m, int seat, Move move) {
        if (m.round() == null) {
            throw new IllegalStateException("no round in progress");
        }
        RoundState round = RoundEngine.applyMove(m.round(), seat, move);
        return new MatchState(
                m.playerCount(),
                m.seed(),
                m.dealer(),
                m.roundNumber(),
                m.plannedRounds(),
                m.playedByDealer(),
                round,
                m.totals(),
                m.history());
    }

    /** Clear a displayed, finished trick so its taker can lead the next one. */
    public static MatchState collectTrick(MatchState m) {
        if (m.round() == null) {
            return m;
        }
        RoundState round = RoundEngine.collectTrick(m.round());
        return new MatchState(
                m.playerCount(),
                m.seed(),
                m.dealer(),
                m.roundNumber(),
                m.plannedRounds(),
                m.playedByDealer(),
                round,
                m.totals(),
                m.history());
    }

    /** Score and close the current round if it is complete; otherwise a no-op. */
    public static MatchState settle(MatchState m) {
        if (m.round() == null || !m.round().isComplete()) {
            return m;
        }
        return settleRound(m, m.round());
    }

    public static MatchState playOut(MatchState m) {
        MatchState cur = m;
        while (cur.round() != null) {
            RoundState round = cur.round();
            int seat = round.currentPlayer();
            cur = applyMove(cur, seat, RoundEngine.legalMoves(round, seat).get(0));
        }
        return cur;
    }

    public static List<Integer> standings(MatchState m) {
        List<Integer> seats = new ArrayList<>();
        for (int seat = 0; seat < m.playerCount(); seat++) {
            seats.add(seat);
        }
        seats.sort(Comparator.comparingInt((Integer seat) -> m.totals()[seat]).reversed());
        return List.copyOf(seats);
    }

    private static MatchState settleRound(MatchState m, RoundState completedRound) {
        RoundResult result = RoundEngine.score(completedRound);

        int[] totals = m.totals().clone();
        for (int seat = 0; seat < m.playerCount(); seat++) {
            totals[seat] += result.points()[seat];
        }
        List<RoundResult> history = new ArrayList<>(m.history());
        history.add(result);

        Set<Contract> playedByDealer = EnumSet.noneOf(Contract.class);
        playedByDealer.addAll(m.playedByDealer());
        playedByDealer.add(completedRound.contract());

        int dealer = m.dealer();
        if (playedByDealer.size() == Contract.values().length) {
            dealer = Seats.next(dealer, m.playerCount());
            playedByDealer = EnumSet.noneOf(Contract.class);
        }

        return new MatchState(
                m.playerCount(),
                m.seed(),
                dealer,
                m.roundNumber() + 1,
                m.plannedRounds(),
                playedByDealer,
                null,
                totals,
                history);
    }

    private static List<List<Card>> deal(int playerCount, long seed) {
        List<Card> deck = Deck.reducedShuffled(playerCount, seed);
        int perHand = deck.size() / playerCount;
        List<List<Card>> hands = new ArrayList<>(playerCount);
        for (int seat = 0; seat < playerCount; seat++) {
            hands.add(new ArrayList<>(deck.subList(seat * perHand, (seat + 1) * perHand)));
        }
        return hands;
    }
}
