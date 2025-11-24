package com.example.krestproxy.config;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;

@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public ObjectPool<Consumer<Object, Object>> consumerPool(
            ConsumerFactory<Object, Object> consumerFactory,
            ConsumerPoolProperties poolProperties) {
        var factory = new PooledKafkaConsumerFactory(consumerFactory);
        var config = new GenericObjectPoolConfig<Consumer<Object, Object>>();
        config.setMaxTotal(poolProperties.getMaxTotal());
        config.setMaxIdle(poolProperties.getMaxIdle());
        config.setMinIdle(poolProperties.getMinIdle());

        logger.info("Configuring consumer pool: maxTotal={}, maxIdle={}, minIdle={}",
                poolProperties.getMaxTotal(), poolProperties.getMaxIdle(), poolProperties.getMinIdle());

        return new GenericObjectPool<>(factory, config);
    }
}
