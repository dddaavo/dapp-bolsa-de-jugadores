package com.unq.dapp.bolsa.shared.error;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;
    private final HttpStatus status;

    public DomainException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public DomainException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
