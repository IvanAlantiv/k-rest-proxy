package com.example.krestproxy.service;

import com.example.krestproxy.config.KafkaProperties;
import com.example.krestproxy.dto.MessageDto;
import com.example.krestproxy.dto.PaginatedResponse;
import com.example.krestproxy.exception.ExecutionNotFoundException;
import com.example.krestproxy.exception.KafkaOperationException;
import com.example.krestproxy.util.CursorUtil;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.commons.pool2.ObjectPool;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KafkaMessageService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageService.class);
    private static final String EXEC_IDS_TOPIC = "execids";

    private static final ThreadLocal<ReusableAvroResources> avroResources = ThreadLocal
            .withInitial(ReusableAvroResources::new);

    private static class ReusableAvroResources {
        final java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        JsonEncoder encoder = null;
        final GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>();
    }

    private final ObjectPool<Consumer<Object, Object>> consumerPool;
    private final KafkaProperties kafkaProperties;

    @Autowired
    public KafkaMessageService(ObjectPool<Consumer<Object, Object>> consumerPool,
            KafkaProperties kafkaProperties) {
        this.consumerPool = consumerPool;
        this.kafkaProperties = kafkaProperties;
        logger.info("KafkaMessageService initialized with consumer pool");
    }

    public List<MessageDto> getMessagesForExecution(List<String> topics, String execId) {
        logger.info("Fetching messages for execution: {}, topics: {}", execId, topics);
        var times = findExecutionTimes(execId);
        // For multi-topic execution fetch, we don't support cursor pagination yet as
        // per requirement "single topic"
        // But we need to adapt to the internal method signature change.
        // We can return just the list from the paginated response for now or keep it as
        // list if we overload internal.
        // Let's overload internal or just unwrap.
        return getMessagesInternal(topics, times.start(), times.end(), execId, null).data();
    }

    public record ExecTime(Instant start, Instant end) {
    }

    @Cacheable(value = "execTimes", key = "#execId")
    protected ExecTime findExecutionTimes(String execId) {
        logger.debug("Cache miss for execution ID: {}, scanning execids topic", execId);

        Consumer<Object, Object> consumer = null;
        try {
            consumer = consumerPool.borrowObject();
            var topicPartition = new TopicPartition(EXEC_IDS_TOPIC, 0);
            consumer.assign(List.of(topicPartition));
            consumer.seekToBeginning(List.of(topicPartition));

            Instant startTime = null;
            Instant endTime = null;

            // Assuming "few days" retention isn't massive, but we should be careful.
            // We scan until we find both or reach end.
            while (startTime == null || endTime == null) {
                var records = consumer.poll(Duration.ofMillis(kafkaProperties.getPollTimeoutMs()));
                if (records.isEmpty()) {
                    break;
                }

                for (var record : records) {
                    String keyStr = record.key().toString();
                    if (execId.equals(keyStr)) {
                        String valStr = record.value().toString();
                        if ("start".equals(valStr)) {
                            startTime = Instant.ofEpochMilli(record.timestamp());
                        } else if ("end".equals(valStr)) {
                            endTime = Instant.ofEpochMilli(record.timestamp());
                        }
                    }
                }
            }

            if (startTime == null || endTime == null) {
                logger.warn("Execution ID not found: {}", execId);
                throw new ExecutionNotFoundException(execId);
            }

            var execTime = new ExecTime(startTime, endTime);
            logger.info("Found execution times for {}: start={}, end={}", execId, startTime, endTime);
            return execTime;

        } catch (ExecutionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error scanning execids topic for execution ID: {}", execId, e);
            throw new KafkaOperationException("Error scanning execids topic", e);
        } finally {
            if (consumer != null) {
                try {
                    consumerPool.returnObject(consumer);
                } catch (Exception e) {
                    logger.error("Error returning consumer to pool", e);
                }
            }
        }
    }

    public PaginatedResponse<MessageDto> getMessages(String topic, Instant startTime, Instant endTime, String cursor) {
        logger.info("Fetching messages from topic: {}, startTime: {}, endTime: {}, cursor: {}", topic, startTime,
                endTime, cursor);
        return getMessagesInternal(List.of(topic), startTime, endTime, null, cursor);
    }

    public PaginatedResponse<MessageDto> getMessagesWithExecId(String topic, Instant startTime, Instant endTime,
            String execId, String cursor) {
        logger.info("Fetching messages from topic: {} with execId: {}, startTime: {}, endTime: {}, cursor: {}",
                topic, execId, startTime, endTime, cursor);
        return getMessagesInternal(List.of(topic), startTime, endTime, execId, cursor);
    }

    public List<MessageDto> getMessagesFromTopics(List<String> topics, Instant startTime, Instant endTime,
            String execId) {
        logger.info("Fetching messages from topics: {} with execId: {}, startTime: {}, endTime: {}",
                topics, execId, startTime, endTime);
        return getMessagesInternal(topics, startTime, endTime, execId, null).data();
    }

    private PaginatedResponse<MessageDto> getMessagesInternal(java.util.Collection<String> topics, Instant startTime,
            Instant endTime, String execId, String cursor) {
        Consumer<Object, Object> consumer = null;
        try {
            logger.debug("Borrowing consumer from pool");
            consumer = consumerPool.borrowObject();

            var partitions = new ArrayList<TopicPartition>();
            for (String topic : topics) {
                var partitionInfos = consumer.partitionsFor(topic);
                if (partitionInfos != null) {
                    partitions.addAll(partitionInfos.stream()
                            .map(pi -> new TopicPartition(topic, pi.partition()))
                            .toList());
                }
            }

            consumer.assign(partitions);

            Map<Integer, Long> cursorOffsets = CursorUtil.parseCursor(cursor);
            Map<Integer, Long> nextCursorOffsets = new HashMap<>();

            // Initialize nextCursorOffsets with cursorOffsets or empty
            if (cursorOffsets != null) {
                nextCursorOffsets.putAll(cursorOffsets);
            }

            // Find offsets for start time if no cursor
            var timestampsToSearch = new HashMap<TopicPartition, Long>();
            if (cursorOffsets == null) {
                for (var partition : partitions) {
                    timestampsToSearch.put(partition, startTime.toEpochMilli());
                }
            }

            var startOffsets = cursorOffsets == null ? consumer.offsetsForTimes(timestampsToSearch) : null;

            // Find offsets for end time
            var endTimestampsToSearch = new HashMap<TopicPartition, Long>();
            for (var partition : partitions) {
                endTimestampsToSearch.put(partition, endTime.toEpochMilli());
            }

            var endOffsets = consumer.offsetsForTimes(endTimestampsToSearch);

            var messages = new ArrayList<MessageDto>();
            int maxMessages = kafkaProperties.getMaxMessagesPerRequest();
            boolean limitReached = false;

            for (var partition : partitions) {
                long seekOffset;
                if (cursorOffsets != null && cursorOffsets.containsKey(partition.partition())) {
                    seekOffset = cursorOffsets.get(partition.partition());
                } else if (startOffsets != null && startOffsets.get(partition) != null) {
                    seekOffset = startOffsets.get(partition).offset();
                } else {
                    // If no cursor and no start offset found (e.g. time is future), skip
                    continue;
                }

                var endOffset = endOffsets.get(partition);

                // If we are already past the end time for this partition, skip
                if (endOffset != null && seekOffset >= endOffset.offset()) {
                    continue;
                }

                consumer.seek(partition, seekOffset);

                var keepReading = true;
                while (keepReading) {
                    var records = consumer.poll(Duration.ofMillis(kafkaProperties.getPollTimeoutMs()));
                    if (records.isEmpty()) {
                        break;
                    }

                    for (var record : records.records(partition)) {
                        // Update next cursor offset to next offset
                        nextCursorOffsets.put(partition.partition(), record.offset() + 1);

                        if (record.timestamp() >= startTime.toEpochMilli()
                                && record.timestamp() <= endTime.toEpochMilli()) {

                            var match = true;
                            if (execId != null) {
                                if (record.key() instanceof GenericRecord keyRecord) {
                                    var execIdObj = keyRecord.get("exec_id");
                                    if (execIdObj == null || !execIdObj.toString().equals(execId)) {
                                        match = false;
                                    }
                                } else {
                                    // If key is not GenericRecord, we can't check exec_id, so mismatch
                                    match = false;
                                }
                            }

                            if (match) {
                                String content = switch (record.value()) {
                                    case GenericRecord genericRecord -> convertAvroToJson(genericRecord);
                                    case null -> null;
                                    case Object o -> o.toString();
                                };

                                messages.add(new MessageDto(
                                        record.topic(),
                                        content,
                                        record.timestamp(),
                                        record.partition(),
                                        record.offset()));

                                // Check if we've reached the maximum message limit
                                if (messages.size() >= maxMessages) {
                                    logger.warn("Reached maximum message limit of {} for topics: {}", maxMessages,
                                            topics);
                                    keepReading = false;
                                    limitReached = true;
                                    break;
                                }
                            }

                        } else if (record.timestamp() > endTime.toEpochMilli()) {
                            keepReading = false;
                            break;
                        }

                        // Optimization: if we have a target end offset, we can check position.
                        if (endOffset != null && record.offset() >= endOffset.offset()) {
                            keepReading = false;
                            break;
                        }
                    }

                    // Safety break if we reached end of partition
                    if (endOffset != null && consumer.position(partition) >= endOffset.offset()) {
                        keepReading = false;
                    }
                }

                // Stop processing additional partitions if we've reached the message limit
                if (limitReached) {
                    break;
                }
            }

            boolean hasMore = false;
            if (limitReached) {
                for (var partition : partitions) {
                    var endOffsetRecord = endOffsets.get(partition);
                    if (endOffsetRecord == null) {
                        continue; // No messages in the time range for this partition
                    }
                    var endOffset = endOffsetRecord.offset();

                    Long currentPosition = nextCursorOffsets.get(partition.partition());

                    if (currentPosition == null) {
                        // This partition hasn't been read from yet.
                        // We need to find its starting offset for this request.
                        if (cursorOffsets != null && cursorOffsets.containsKey(partition.partition())) {
                            currentPosition = cursorOffsets.get(partition.partition());
                        } else if (startOffsets != null && startOffsets.get(partition) != null) {
                            currentPosition = startOffsets.get(partition).offset();
                        }
                    }

                    if (currentPosition != null && currentPosition < endOffset) {
                        hasMore = true;
                        break;
                    }
                }
            }

            String nextCursor = hasMore ? CursorUtil.createCursor(nextCursorOffsets) : null;

            logger.info("Retrieved {} messages from topics: {}", messages.size(), topics);
            return new PaginatedResponse<>(messages, nextCursor, hasMore);
        } catch (Exception e) {
            logger.error("Error fetching messages from Kafka topics: {}", topics, e);
            throw new KafkaOperationException("Error fetching messages from Kafka", e);
        } finally {
            if (consumer != null) {
                try {
                    consumerPool.returnObject(consumer);
                } catch (Exception e) {
                    logger.error("Error returning consumer to pool", e);
                }
            }
        }
    }

    private String convertAvroToJson(GenericRecord record) {
        try {
            var resources = avroResources.get();
            var outputStream = resources.outputStream;
            outputStream.reset();

            resources.encoder = EncoderFactory.get()
                    .jsonEncoder(record.getSchema(), outputStream);

            resources.writer.setSchema(record.getSchema());
            resources.writer.write(record, resources.encoder);
            resources.encoder.flush();
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            logger.error("Error converting Avro to JSON", e);
            throw new KafkaOperationException("Error converting Avro to JSON", e);
        }
    }
}
