package com.example.krestproxy.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for consistent error responses across the
 * application.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ExecutionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleExecutionNotFound(ExecutionNotFoundException ex) {
        logger.warn("Execution not found: {}", ex.getExecutionId());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), "EXECUTION_NOT_FOUND");
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException ex) {
        logger.warn("Invalid request: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_REQUEST");
    }

    @ExceptionHandler(KafkaOperationException.class)
    public ResponseEntity<Map<String, Object>> handleKafkaOperation(KafkaOperationException ex) {
        logger.error("Kafka operation failed", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error processing Kafka request", "KAFKA_ERROR");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        logger.warn("Validation failed: {}", ex.getMessage());
        StringBuilder errors = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(
                error -> errors.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; "));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, errors.toString(), "VALIDATION_ERROR");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        logger.warn("Type mismatch: {}", ex.getMessage());
        String message = String.format("Invalid value for parameter '%s'", ex.getName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, "TYPE_MISMATCH");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        logger.error("Unexpected error", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", "INTERNAL_ERROR");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String message, String errorCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("errorCode", errorCode);
        return new ResponseEntity<>(body, status);
    }
}
