package com.example.krestproxy.exception;

/**
 * Exception thrown when an execution ID is not found in the execids topic.
 */
public class ExecutionNotFoundException extends ServiceException {

    private final String executionId;

    public ExecutionNotFoundException(String executionId) {
        super("Could not find start and/or end time for execution ID: " + executionId);
        this.executionId = executionId;
    }

    public String getExecutionId() {
        return executionId;
    }
}
