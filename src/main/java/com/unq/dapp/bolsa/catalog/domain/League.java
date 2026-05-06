package com.unq.dapp.bolsa.catalog.domain;

public enum League {
    PREMIER_LEAGUE("Premier League"),
    BUNDESLIGA("Bundesliga"),
    LA_LIGA("La Liga"),
    SERIE_A("Serie A"),
    LIGUE_1("Ligue 1");

    private final String label;

    League(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
