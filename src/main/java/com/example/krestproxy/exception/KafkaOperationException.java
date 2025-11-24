package com.example.krestproxy.exception;

/**
 * Exception thrown when a Kafka operation fails.
 */
public class KafkaOperationException extends ServiceException {

    public KafkaOperationException(String message) {
        super(message);
    }

    public KafkaOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
