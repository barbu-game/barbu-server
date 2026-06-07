package com.barbu.engine.round;

import com.barbu.engine.model.Contract;
import java.util.Map;

public record RoundResult(Contract contract, int[] points) {
    public RoundResult {
        points = points.clone();
    }

    public static RoundResult fromMap(Contract contract, Map<Integer, Integer> bySeat) {
        int[] arr = new int[bySeat.size()];
        for (Map.Entry<Integer, Integer> e : bySeat.entrySet()) {
            arr[e.getKey()] = e.getValue();
        }
        return new RoundResult(contract, arr);
    }
}
