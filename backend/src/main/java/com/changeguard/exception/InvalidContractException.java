package com.changeguard.exception;

/** Expected contract-input failure, translated to HTTP 400 by ApiErrors. */
public class InvalidContractException extends IllegalArgumentException {
    public InvalidContractException(String message) {
        super(message);
    }
}
