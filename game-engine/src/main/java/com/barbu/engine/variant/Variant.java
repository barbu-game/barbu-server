package com.barbu.engine.variant;

import com.barbu.engine.model.Contract;
import com.barbu.engine.scoring.TrickScoringRule;
import java.util.List;
import java.util.Map;

/** A playable ruleset: ordered contracts plus the scoring rule for each trick contract. */
public record Variant(
        String id,
        String name,
        String description,
        List<Contract> contracts,
        Map<Contract, TrickScoringRule> trickRules) {

    public Variant {
        contracts = List.copyOf(contracts);
        trickRules = Map.copyOf(trickRules);
    }
}
