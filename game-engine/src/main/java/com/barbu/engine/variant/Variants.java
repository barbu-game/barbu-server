package com.barbu.engine.variant;

import com.barbu.engine.card.Card;
import com.barbu.engine.model.Contract;
import com.barbu.engine.model.ScoringConfig;
import com.barbu.engine.scoring.CardPenalty;
import com.barbu.engine.scoring.CombinedRule;
import com.barbu.engine.scoring.LastTricksPenalty;
import com.barbu.engine.scoring.NormalizedCardPenalty;
import com.barbu.engine.scoring.NormalizedTrickPenalty;
import com.barbu.engine.scoring.TrickPenalty;
import com.barbu.engine.scoring.TrickScoringRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Variants {
    private Variants() {}

    private static final TrickScoringRule TRICKS = new TrickPenalty(ScoringConfig.PER_TRICK);
    private static final TrickScoringRule HEARTS = new CardPenalty(Card::isHeart, ScoringConfig.PER_HEART, "heart");
    private static final TrickScoringRule QUEENS = new CardPenalty(Card::isQueen, ScoringConfig.PER_QUEEN, "queen");

    // The five-contract table normalises every contract to the same per-round total (spec §2.4).
    private static final TrickScoringRule TRICKS_60 = new NormalizedTrickPenalty();
    private static final TrickScoringRule HEARTS_60 = new NormalizedCardPenalty(Card::isHeart, "heart");
    private static final TrickScoringRule QUEENS_60 = new NormalizedCardPenalty(Card::isQueen, "queen");
    private static final TrickScoringRule RED_KINGS_60 = new NormalizedCardPenalty(Card::isRedKing, "red king");
    private static final TrickScoringRule KING_OF_HEARTS =
            new CardPenalty(Card::isKingOfHearts, ScoringConfig.PER_KING_OF_HEARTS, "King of Hearts");
    private static final TrickScoringRule JACKS = new CardPenalty(Card::isJack, ScoringConfig.PER_JACK, "Jack");
    private static final TrickScoringRule LAST_TWO = new LastTricksPenalty(2, ScoringConfig.PER_LAST_TRICK);
    private static final TrickScoringRule SALAD =
            new CombinedRule(List.of(TRICKS, HEARTS, QUEENS, KING_OF_HEARTS, LAST_TWO));
    private static final TrickScoringRule SALAD_EXTENDED =
            new CombinedRule(List.of(TRICKS, HEARTS, QUEENS, KING_OF_HEARTS, JACKS, LAST_TWO));

    public static final Variant DEVELOPER = new Variant(
            "developer",
            "Developer variant",
            "The original five-contract ruleset: avoid tricks, hearts, queens and red kings, then the montante.",
            List.of(
                    Contract.NO_TRICKS,
                    Contract.NO_HEARTS,
                    Contract.NO_QUEENS,
                    Contract.NO_RED_KINGS,
                    Contract.MONTANTE),
            ruleMap(
                    Map.entry(Contract.NO_TRICKS, TRICKS_60),
                    Map.entry(Contract.NO_HEARTS, HEARTS_60),
                    Map.entry(Contract.NO_QUEENS, QUEENS_60),
                    Map.entry(Contract.NO_RED_KINGS, RED_KINGS_60)));

    public static final Variant CLASSIC = new Variant(
            "classic",
            "Classic Barbu",
            "The traditional seven contracts: tricks, hearts, queens, the King of Hearts (le Barbu),"
                    + " the last two tricks, the montante, and the salad.",
            List.of(
                    Contract.NO_TRICKS,
                    Contract.NO_HEARTS,
                    Contract.NO_QUEENS,
                    Contract.NO_KING_OF_HEARTS,
                    Contract.NO_LAST_TWO_TRICKS,
                    Contract.MONTANTE,
                    Contract.SALADE),
            ruleMap(
                    Map.entry(Contract.NO_TRICKS, TRICKS),
                    Map.entry(Contract.NO_HEARTS, HEARTS),
                    Map.entry(Contract.NO_QUEENS, QUEENS),
                    Map.entry(Contract.NO_KING_OF_HEARTS, KING_OF_HEARTS),
                    Map.entry(Contract.NO_LAST_TWO_TRICKS, LAST_TWO),
                    Map.entry(Contract.SALADE, SALAD)));

    public static final Variant QUICK = new Variant(
            "quick",
            "Quick Barbu",
            "A shorter five-contract table: tricks, hearts, queens, the King of Hearts, and the montante.",
            List.of(
                    Contract.NO_TRICKS,
                    Contract.NO_HEARTS,
                    Contract.NO_QUEENS,
                    Contract.NO_KING_OF_HEARTS,
                    Contract.MONTANTE),
            ruleMap(
                    Map.entry(Contract.NO_TRICKS, TRICKS),
                    Map.entry(Contract.NO_HEARTS, HEARTS),
                    Map.entry(Contract.NO_QUEENS, QUEENS),
                    Map.entry(Contract.NO_KING_OF_HEARTS, KING_OF_HEARTS)));

    public static final Variant EXTENDED = new Variant(
            "extended",
            "Extended Barbu",
            "Classic Barbu plus a no-Jacks contract — eight contracts in all.",
            List.of(
                    Contract.NO_TRICKS,
                    Contract.NO_HEARTS,
                    Contract.NO_QUEENS,
                    Contract.NO_KING_OF_HEARTS,
                    Contract.NO_JACKS,
                    Contract.NO_LAST_TWO_TRICKS,
                    Contract.MONTANTE,
                    Contract.SALADE),
            ruleMap(
                    Map.entry(Contract.NO_TRICKS, TRICKS),
                    Map.entry(Contract.NO_HEARTS, HEARTS),
                    Map.entry(Contract.NO_QUEENS, QUEENS),
                    Map.entry(Contract.NO_KING_OF_HEARTS, KING_OF_HEARTS),
                    Map.entry(Contract.NO_JACKS, JACKS),
                    Map.entry(Contract.NO_LAST_TWO_TRICKS, LAST_TWO),
                    Map.entry(Contract.SALADE, SALAD_EXTENDED)));

    private static final Map<String, Variant> BY_ID = new LinkedHashMap<>();

    static {
        for (Variant v : List.of(DEVELOPER, CLASSIC, QUICK, EXTENDED)) {
            BY_ID.put(v.id(), v);
        }
    }

    public static List<Variant> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Resolve a variant by id, or {@code null} if unknown. */
    public static Variant byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    @SafeVarargs
    private static Map<Contract, TrickScoringRule> ruleMap(Map.Entry<Contract, TrickScoringRule>... entries) {
        Map<Contract, TrickScoringRule> map = new LinkedHashMap<>();
        for (Map.Entry<Contract, TrickScoringRule> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }
}
