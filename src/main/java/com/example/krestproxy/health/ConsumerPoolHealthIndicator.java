package com.example.krestproxy.health;

import org.apache.commons.pool2.ObjectPool;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that checks consumer pool status.
 */
@Component
public class ConsumerPoolHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerPoolHealthIndicator.class);
    private final ObjectPool<Consumer<Object, Object>> consumerPool;

    public ConsumerPoolHealthIndicator(ObjectPool<Consumer<Object, Object>> consumerPool) {
        this.consumerPool = consumerPool;
    }

    @Override
    public Health health() {
        try {
            int numActive = consumerPool.getNumActive();
            int numIdle = consumerPool.getNumIdle();

            logger.debug("Consumer pool health check: active={}, idle={}", numActive, numIdle);

            // Check if we can borrow a consumer
            Consumer<Object, Object> consumer = consumerPool.borrowObject();
            consumerPool.returnObject(consumer);

            return Health.up()
                    .withDetail("activeConsumers", numActive)
                    .withDetail("idleConsumers", numIdle)
                    .withDetail("totalConsumers", numActive + numIdle)
                    .build();
        } catch (Exception e) {
            logger.error("Consumer pool health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
