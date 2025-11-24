package com.example.krestproxy.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Health indicator that checks Kafka broker connectivity.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Override
    public Health health() {
        try {
            Properties config = new Properties();
            config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);

            try (AdminClient adminClient = AdminClient.create(config)) {
                // Try to get cluster info as a health check
                var nodes = adminClient.describeCluster()
                        .nodes()
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);

                logger.debug("Kafka health check passed: {} nodes available", nodes.size());
                return Health.up()
                        .withDetail("bootstrapServers", bootstrapServers)
                        .withDetail("nodes", nodes.size())
                        .build();
            }
        } catch (Exception e) {
            logger.error("Kafka health check failed", e);
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
