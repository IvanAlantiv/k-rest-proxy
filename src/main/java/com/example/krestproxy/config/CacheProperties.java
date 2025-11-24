package com.example.krestproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Configuration properties for execution time cache.
 */
@Component
@ConfigurationProperties(prefix = "app.cache")
@Validated
public class CacheProperties {

    /**
     * Time-to-live for cache entries in minutes.
     */
    @Min(1)
    @Max(1440)
    private int execTimeTtlMinutes = 60;

    /**
     * Maximum size of the cache.
     */
    @Min(10)
    @Max(100000)
    private int maxSize = 1000;

    public int getExecTimeTtlMinutes() {
        return execTimeTtlMinutes;
    }

    public void setExecTimeTtlMinutes(int execTimeTtlMinutes) {
        this.execTimeTtlMinutes = execTimeTtlMinutes;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }
}
