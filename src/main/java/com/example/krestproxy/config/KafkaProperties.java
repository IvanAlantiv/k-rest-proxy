package com.example.krestproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Configuration properties for Kafka operations.
 */
@Component
@ConfigurationProperties(prefix = "app.kafka")
@Validated
public class KafkaProperties {

    /**
     * Poll timeout in milliseconds.
     */
    @Min(10)
    @Max(10000)
    private long pollTimeoutMs = 100;

    /**
     * Maximum messages to return per request.
     */
    @Min(1)
    @Max(100000)
    private int maxMessagesPerRequest = 10000;

    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    public void setPollTimeoutMs(long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public int getMaxMessagesPerRequest() {
        return maxMessagesPerRequest;
    }

    public void setMaxMessagesPerRequest(int maxMessagesPerRequest) {
        this.maxMessagesPerRequest = maxMessagesPerRequest;
    }
}
