package com.unq.dapp.bolsa.catalog.domain;

import com.unq.dapp.bolsa.shared.error.DomainException;
import org.springframework.http.HttpStatus;

public class PlayerNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PlayerNotFoundException(Long id) {
        super("PLAYER_NOT_FOUND", "Jugador no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
