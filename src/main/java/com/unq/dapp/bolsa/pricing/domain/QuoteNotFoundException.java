package com.unq.dapp.bolsa.pricing.domain;

import com.unq.dapp.bolsa.shared.error.DomainException;
import org.springframework.http.HttpStatus;

public class QuoteNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public QuoteNotFoundException(Long playerId) {
        super("QUOTE_NOT_FOUND", "No hay cotización para el jugador: " + playerId, HttpStatus.NOT_FOUND);
    }
}
