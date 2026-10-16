package com.barbu.rami.round;

import com.barbu.rami.card.RamiCard;
import com.barbu.rami.card.RamiDeck;
import com.barbu.rami.meld.Meld;
import com.barbu.rami.meld.MeldKind;
import com.barbu.rami.meld.MeldRules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RamiRoundEngine {
    public static final int OPENING_MIN = 51;
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 5;
    /** Endless pile cycling (nobody can use what circulates) ends the round with no winner. */
    public static final int MAX_RECYCLES = 3;

    private RamiRoundEngine() {}

    public static RamiRound deal(int playerCount, int dealer, long seed) {
        if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
            throw new IllegalArgumentException("playerCount out of range: " + playerCount);
        }
        if (dealer < 0 || dealer >= playerCount) {
            throw new IllegalArgumentException("dealer out of range: " + dealer);
        }
        List<RamiCard> deck = RamiDeck.shuffled(seed);
        int first = (dealer + 1) % playerCount;
        List<List<RamiCard>> hands = new ArrayList<>();
        int pos = 0;
        for (int seat = 0; seat < playerCount; seat++) {
            int size = seat == first ? 14 : 13;
            hands.add(deck.subList(pos, pos + size));
            pos += size;
        }
        return setUp(
                first,
                Phase.PLAY,
                hands,
                deck.subList(pos, deck.size()),
                List.of(),
                List.of(),
                Collections.nCopies(playerCount, false));
    }

    /** Arbitrary position at the start of {@code current}'s turn; trusts its input (tests, restore). */
    public static RamiRound setUp(
            int current,
            Phase phase,
            List<List<RamiCard>> hands,
            List<RamiCard> stock,
            List<RamiCard> discard,
            List<Meld> melds,
            List<Boolean> opened) {
        int nextMeldId = melds.stream().mapToInt(Meld::id).max().orElse(-1) + 1;
        Table t = new Table(new RamiRound(
                hands, stock, discard, melds, opened, current, phase, Set.of(), null, nextMeldId, null, 0));
        t.snapshotTurnStart();
        return t.freeze();
    }

    public static RamiRound apply(RamiRound round, int seat, RamiMove move) {
        if (round.phase() == Phase.FINISHED) {
            throw new IllegalStateException("round is over");
        }
        if (move == null) {
            throw iae("missing move");
        }
        if (seat != round.current()) {
            throw iae("not seat " + seat + "'s turn");
        }
        if (move instanceof RamiMove.ResetTurn) {
            return resetTurn(round);
        }
        Table t = new Table(round);
        switch (move) {
            case RamiMove.Draw d -> draw(t, d.source());
            case RamiMove.Discard d -> discard(t, d.cardId());
            case RamiMove.Open o -> open(t, o.melds());
            case RamiMove.NewMeld m -> newMeld(t, m.cards());
            case RamiMove.LayOff l -> layOff(t, l);
            case RamiMove.SwapJoker s -> swapJoker(t, s);
            case RamiMove.ResetTurn r -> throw new IllegalStateException("handled above");
        }
        return t.freeze();
    }

    private static void draw(Table t, RamiMove.Source source) {
        requirePhase(t, Phase.DRAW);
        RamiCard card;
        if (source == RamiMove.Source.DISCARD) {
            if (t.hand().size() <= 1) {
                throw iae("cannot take the discard with a single card");
            }
            if (t.discard.isEmpty()) {
                throw iae("the discard pile is empty");
            }
            card = t.discard.remove(t.discard.size() - 1);
        } else {
            if (t.stock.isEmpty() && t.recycles < MAX_RECYCLES) {
                recycle(t);
            }
            if (t.stock.isEmpty()) {
                t.phase = Phase.FINISHED;
                return;
            }
            card = t.stock.remove(0);
        }
        t.hand().add(card);
        t.phase = Phase.PLAY;
        // Drawing commits: a reset undoes plays, never the draw (a stock card has been seen).
        t.snapshotTurnStart();
    }

    // Turning the pile over puts its bottom card on top of the new stock.
    private static void recycle(Table t) {
        if (t.discard.size() <= 1) {
            return;
        }
        RamiCard top = t.discard.remove(t.discard.size() - 1);
        t.stock.addAll(t.discard);
        t.discard.clear();
        t.discard.add(top);
        t.recycles++;
    }

    private static void discard(Table t, int cardId) {
        requirePhase(t, Phase.PLAY);
        if (!t.mustPlay.isEmpty()) {
            throw iae("cards " + t.mustPlay + " must be laid down first");
        }
        RamiCard card = resolve(t, List.of(cardId)).get(0);
        t.hand().remove(card);
        t.discard.add(card);
        if (t.hand().isEmpty()) {
            t.winner = t.current;
            t.phase = Phase.FINISHED;
            return;
        }
        t.current = (t.current + 1) % t.hands.size();
        t.phase = Phase.DRAW;
        t.snapshotTurnStart();
    }

    private static RamiRound resetTurn(RamiRound round) {
        if (round.phase() != Phase.PLAY) {
            throw iae("nothing to reset before drawing");
        }
        Table t = new Table(round.turnStart());
        t.snapshotTurnStart();
        return t.freeze();
    }

    private static List<RamiCard> resolve(Table t, List<Integer> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw iae("the same card appears twice");
        }
        List<RamiCard> cards = new ArrayList<>();
        for (int id : ids) {
            cards.add(t.hand().stream()
                    .filter(c -> c.id() == id)
                    .findFirst()
                    .orElseThrow(() -> iae("card " + id + " is not in hand")));
        }
        return cards;
    }

    private static void requirePhase(Table t, Phase phase) {
        if (t.phase != phase) {
            throw iae("expected phase " + phase + " but was " + t.phase);
        }
    }

    private static IllegalArgumentException iae(String message) {
        return new IllegalArgumentException(message);
    }

    private static void open(Table t, List<List<Integer>> groups) {
        requirePhase(t, Phase.PLAY);
        if (t.opened.get(t.current)) {
            throw iae("already opened");
        }
        if (groups.isEmpty()) {
            throw iae("nothing to open with");
        }
        List<RamiCard> all = resolve(t, groups.stream().flatMap(List::stream).toList());
        List<List<RamiCard>> melds = groups.stream().map(g -> resolve(t, g)).toList();
        int total = 0;
        boolean hasRun = false;
        for (List<RamiCard> meld : melds) {
            hasRun |= requireMeld(meld) == MeldKind.RUN;
            total += MeldRules.points(meld);
        }
        if (total < OPENING_MIN) {
            throw iae("opening is worth " + total + ", needs " + OPENING_MIN);
        }
        if (!hasRun) {
            throw iae("opening needs at least one run");
        }
        removeFromHand(t, all);
        melds.forEach(meld -> addMeld(t, meld));
        t.opened.set(t.current, true);
    }

    private static void newMeld(Table t, List<Integer> ids) {
        requireOpened(t);
        List<RamiCard> cards = resolve(t, ids);
        requireMeld(cards);
        removeFromHand(t, cards);
        addMeld(t, cards);
    }

    private static void layOff(Table t, RamiMove.LayOff move) {
        requireOpened(t);
        int index = meldIndex(t, move.meldId());
        Meld meld = t.melds.get(index);
        List<Integer> addedIds = new ArrayList<>(move.before());
        addedIds.addAll(move.after());
        if (addedIds.isEmpty()) {
            throw iae("nothing to lay off");
        }
        List<RamiCard> added = resolve(t, addedIds);
        List<RamiCard> cards = new ArrayList<>(resolve(t, move.before()));
        cards.addAll(meld.cards());
        cards.addAll(resolve(t, move.after()));
        if (MeldRules.kind(cards).orElse(null) != meld.kind()) {
            throw iae("cards do not extend meld " + meld.id());
        }
        removeFromHand(t, added);
        t.melds.set(index, new Meld(meld.id(), meld.owner(), meld.kind(), List.copyOf(cards)));
    }

    private static void swapJoker(Table t, RamiMove.SwapJoker move) {
        requireOpened(t);
        int index = meldIndex(t, move.meldId());
        Meld meld = t.melds.get(index);
        RamiCard real = resolve(t, List.of(move.cardId())).get(0);
        if (real.isJoker()) {
            throw iae("a joker cannot replace a joker");
        }
        List<RamiCard> cards = new ArrayList<>(meld.cards());
        int jokerAt = -1;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).isJoker()) {
                jokerAt = i;
            }
        }
        if (jokerAt < 0) {
            throw iae("meld " + meld.id() + " has no joker");
        }
        // In a three-card set the joker's suit is ambiguous: it is only taken from a full four.
        if (meld.kind() == MeldKind.SET && cards.size() < 4) {
            throw iae("a set's joker can only be taken from a four of a kind");
        }
        RamiCard joker = cards.set(jokerAt, real);
        if (MeldRules.kind(cards).orElse(null) != meld.kind()) {
            throw iae("card does not stand for the joker");
        }
        t.hand().remove(real);
        t.hand().add(joker);
        t.mustPlay.remove(real.id());
        t.mustPlay.add(joker.id());
        t.melds.set(index, new Meld(meld.id(), meld.owner(), meld.kind(), List.copyOf(cards)));
    }

    private static void requireOpened(Table t) {
        requirePhase(t, Phase.PLAY);
        if (!t.opened.get(t.current)) {
            throw iae("lay down 51 points first");
        }
    }

    private static MeldKind requireMeld(List<RamiCard> cards) {
        return MeldRules.kind(cards).orElseThrow(() -> iae("not a valid meld"));
    }

    private static void removeFromHand(Table t, List<RamiCard> cards) {
        t.hand().removeAll(cards);
        if (t.hand().isEmpty()) {
            throw iae("a card must be kept for the final discard");
        }
        cards.forEach(c -> t.mustPlay.remove(c.id()));
    }

    private static void addMeld(Table t, List<RamiCard> cards) {
        t.melds.add(new Meld(t.nextMeldId++, t.current, requireMeld(cards), List.copyOf(cards)));
    }

    private static int meldIndex(Table t, int meldId) {
        for (int i = 0; i < t.melds.size(); i++) {
            if (t.melds.get(i).id() == meldId) {
                return i;
            }
        }
        throw iae("no meld " + meldId);
    }

    /** Mutable working copy; every move edits one and freezes it back into a record. */
    private static final class Table {
        final List<List<RamiCard>> hands = new ArrayList<>();
        final List<RamiCard> stock;
        final List<RamiCard> discard;
        final List<Meld> melds;
        final List<Boolean> opened;
        int current;
        Phase phase;
        final Set<Integer> mustPlay;
        RamiRound turnStart;
        int nextMeldId;
        Integer winner;
        int recycles;

        Table(RamiRound r) {
            r.hands().forEach(h -> hands.add(new ArrayList<>(h)));
            stock = new ArrayList<>(r.stock());
            discard = new ArrayList<>(r.discard());
            melds = new ArrayList<>(r.melds());
            opened = new ArrayList<>(r.opened());
            current = r.current();
            phase = r.phase();
            mustPlay = new HashSet<>(r.mustPlay());
            turnStart = r.turnStart();
            nextMeldId = r.nextMeldId();
            winner = r.winner();
            recycles = r.recycles();
        }

        List<RamiCard> hand() {
            return hands.get(current);
        }

        RamiRound freeze() {
            return new RamiRound(
                    hands.stream().map(List::copyOf).toList(),
                    List.copyOf(stock),
                    List.copyOf(discard),
                    List.copyOf(melds),
                    List.copyOf(opened),
                    current,
                    phase,
                    Set.copyOf(mustPlay),
                    turnStart,
                    nextMeldId,
                    winner,
                    recycles);
        }

        void snapshotTurnStart() {
            turnStart = null;
            turnStart = freeze();
        }
    }
}
