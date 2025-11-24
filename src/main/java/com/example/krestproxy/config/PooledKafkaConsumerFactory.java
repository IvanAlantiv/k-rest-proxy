package com.example.krestproxy.config;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;

public class PooledKafkaConsumerFactory extends BasePooledObjectFactory<Consumer<Object, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(PooledKafkaConsumerFactory.class);
    private final ConsumerFactory<Object, Object> consumerFactory;

    public PooledKafkaConsumerFactory(ConsumerFactory<Object, Object> consumerFactory) {
        this.consumerFactory = consumerFactory;
        logger.info("PooledKafkaConsumerFactory initialized");
    }

    @Override
    public Consumer<Object, Object> create() throws Exception {
        logger.debug("Creating new Kafka consumer");
        return consumerFactory.createConsumer();
    }

    @Override
    public PooledObject<Consumer<Object, Object>> wrap(Consumer<Object, Object> consumer) {
        return new DefaultPooledObject<>(consumer);
    }

    @Override
    public void destroyObject(PooledObject<Consumer<Object, Object>> p) throws Exception {
        if (p != null && p.getObject() != null) {
            logger.debug("Destroying Kafka consumer");
            p.getObject().close();
        }
    }
}
