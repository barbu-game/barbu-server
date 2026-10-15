package com.barbu.rami.round;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.meld.Meld;
import java.util.List;
import java.util.Set;

/**
 * Stock is drawn from index 0; the discard top is its last element. {@code mustPlay} holds the
 * taken discard card and any reclaimed joker, which must reach the table before discarding.
 */
public record RamiRound(
        List<List<RamiCard>> hands,
        List<RamiCard> stock,
        List<RamiCard> discard,
        List<Meld> melds,
        List<Boolean> opened,
        int current,
        Phase phase,
        Set<Integer> mustPlay,
        boolean stockOnly,
        RamiRound turnStart,
        int nextMeldId,
        Integer winner,
        int recycles) {

    public int playerCount() {
        return hands.size();
    }

    public List<RamiCard> hand(int seat) {
        return hands.get(seat);
    }

    /** The winner had laid nothing down when their final turn began. */
    public boolean ramiSec() {
        return winner != null && !turnStart.opened().get(winner);
    }
}
