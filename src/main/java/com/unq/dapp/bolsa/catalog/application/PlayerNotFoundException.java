package com.unq.dapp.bolsa.catalog.application;

public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(Long id) {
        super("Jugador no encontrado: " + id);
    }
}
