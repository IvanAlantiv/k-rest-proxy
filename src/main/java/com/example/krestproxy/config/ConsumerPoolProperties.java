package com.example.krestproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Configuration properties for Kafka consumer pool.
 */
@Component
@ConfigurationProperties(prefix = "app.kafka.consumer-pool")
@Validated
public class ConsumerPoolProperties {

    /**
     * Maximum number of consumers in the pool.
     */
    @Min(1)
    @Max(100)
    private int maxTotal = 10;

    /**
     * Maximum number of idle consumers to keep in the pool.
     */
    @Min(0)
    @Max(50)
    private int maxIdle = 5;

    /**
     * Minimum number of idle consumers to maintain in the pool.
     */
    @Min(0)
    @Max(10)
    private int minIdle = 1;

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }
}
