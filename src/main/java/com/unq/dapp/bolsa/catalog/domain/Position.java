package com.unq.dapp.bolsa.catalog.domain;

public enum Position {
    GK("Goalkeeper"),
    DF("Defender"),
    MF("Midfielder"),
    FW("Forward");

    private final String label;

    Position(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
