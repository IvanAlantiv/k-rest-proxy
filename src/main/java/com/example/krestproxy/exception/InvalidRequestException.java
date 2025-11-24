package com.example.krestproxy.exception;

/**
 * Exception thrown when request validation fails.
 */
public class InvalidRequestException extends ServiceException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
