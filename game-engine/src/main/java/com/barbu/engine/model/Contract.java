package com.barbu.engine.model;

public enum Contract {
    NO_TRICKS(ContractType.TRICK_TAKING),
    NO_HEARTS(ContractType.TRICK_TAKING),
    NO_QUEENS(ContractType.TRICK_TAKING),
    NO_RED_KINGS(ContractType.TRICK_TAKING),
    NO_KING_OF_HEARTS(ContractType.TRICK_TAKING),
    NO_JACKS(ContractType.TRICK_TAKING),
    NO_LAST_TWO_TRICKS(ContractType.TRICK_TAKING),
    SALADE(ContractType.TRICK_TAKING),
    MONTANTE(ContractType.MONTANTE);

    private final ContractType type;

    Contract(ContractType type) {
        this.type = type;
    }

    public ContractType type() {
        return type;
    }
}
