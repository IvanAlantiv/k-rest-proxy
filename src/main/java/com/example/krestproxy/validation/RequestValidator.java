package com.example.krestproxy.validation;

import com.example.krestproxy.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Utility class for validating API requests.
 */
@Component
public class RequestValidator {

    private static final Pattern TOPIC_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final long MAX_TIME_WINDOW_HOURS = 24;
    private static final Pattern EXEC_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Validates that a topic name follows Kafka naming conventions.
     */
    public void validateTopicName(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new InvalidRequestException("Topic name cannot be null or empty");
        }
        if (topic.length() > 255) {
            throw new InvalidRequestException("Topic name cannot exceed 255 characters");
        }
        if (!TOPIC_NAME_PATTERN.matcher(topic).matches()) {
            throw new InvalidRequestException(
                    "Topic name can only contain alphanumeric characters, dots, underscores, and hyphens");
        }
    }

    /**
     * Validates that the time range is valid and within acceptable limits.
     */
    public void validateTimeRange(Instant startTime, Instant endTime) {
        if (startTime == null) {
            throw new InvalidRequestException("Start time cannot be null");
        }
        if (endTime == null) {
            throw new InvalidRequestException("End time cannot be null");
        }
        if (startTime.isAfter(endTime)) {
            throw new InvalidRequestException("Start time must be before end time");
        }

        Duration window = Duration.between(startTime, endTime);
        if (window.toHours() > MAX_TIME_WINDOW_HOURS) {
            throw new InvalidRequestException(
                    String.format("Time window cannot exceed %d hours", MAX_TIME_WINDOW_HOURS));
        }

        if (endTime.isAfter(Instant.now().plus(Duration.ofHours(1)))) {
            throw new InvalidRequestException("End time cannot be more than 1 hour in the future");
        }
    }

    /**
     * Validates execution ID format.
     */
    public void validateExecutionId(String execId) {
        if (execId == null || execId.isBlank()) {
            throw new InvalidRequestException("Execution ID cannot be null or empty");
        }
        if (execId.length() > 100) {
            throw new InvalidRequestException("Execution ID cannot exceed 100 characters");
        }
        if (!EXEC_ID_PATTERN.matcher(execId).matches()) {
            throw new InvalidRequestException(
                    "Execution ID can only contain alphanumeric characters, underscores, and hyphens");
        }
    }
}
