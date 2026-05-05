package com.unq.dapp.bolsa.catalog.application;

public class PlayerNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlayerNotFoundException(Long id) {
        super("Jugador no encontrado: " + id);
    }
}
