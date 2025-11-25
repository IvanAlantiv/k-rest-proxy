package com.example.krestproxy.service;

import com.example.krestproxy.config.KafkaProperties;
import com.example.krestproxy.dto.MessageDto;
import com.example.krestproxy.dto.PaginatedResponse;
import com.example.krestproxy.util.CursorUtil;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.pool2.ObjectPool;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaMessageServiceTest {

        @Mock
        private ObjectPool<Consumer<Object, Object>> consumerPool;

        @Mock
        private Consumer<Object, Object> consumer;

        @Mock
        private KafkaProperties kafkaProperties;

        private KafkaMessageService kafkaMessageService;

        @BeforeEach
        void setUp() throws Exception {
                when(kafkaProperties.getMaxMessagesPerRequest()).thenReturn(10000);
                when(kafkaProperties.getPollTimeoutMs()).thenReturn(100L);
                kafkaMessageService = new KafkaMessageService(consumerPool, kafkaProperties);
        }

        @Test
        void getMessages_shouldReturnMessagesWithinTimeRange() throws Exception {
                String topic = "test-topic";
                Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
                Instant endTime = Instant.parse("2023-01-01T10:05:00Z");
                TopicPartition partition0 = new TopicPartition(topic, 0);

                when(consumerPool.borrowObject()).thenReturn(consumer);
                when(consumer.partitionsFor(topic)).thenReturn(
                                Collections.singletonList(
                                                new org.apache.kafka.common.PartitionInfo(topic, 0, null, null, null)));

                // Mock offsetsForTimes
                Map<TopicPartition, OffsetAndTimestamp> startOffsets = new HashMap<>();
                startOffsets.put(partition0, new OffsetAndTimestamp(0L, startTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == startTime.toEpochMilli())))
                                .thenReturn(startOffsets);

                Map<TopicPartition, OffsetAndTimestamp> endOffsets = new HashMap<>();
                endOffsets.put(partition0, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == endTime.toEpochMilli())))
                                .thenReturn(endOffsets);

                // Mock poll
                ConsumerRecord<Object, Object> record1 = new ConsumerRecord<>(topic, 0, 0L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg1",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record2 = new ConsumerRecord<>(topic, 0, 5L,
                                startTime.toEpochMilli() + 1000,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg2",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(partition0, Arrays.asList(record1, record2));
                ConsumerRecords<Object, Object> records = new ConsumerRecords<>(recordsMap);

                when(consumer.poll(any())).thenReturn(records)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

                PaginatedResponse<MessageDto> response = kafkaMessageService.getMessages(topic, startTime,
                                endTime, null);
                List<MessageDto> messages = response.data();

                assertEquals(2, messages.size());
                assertEquals("msg1", messages.get(0).content());
                assertEquals(topic, messages.get(0).topicName());
                assertEquals("msg2", messages.get(1).content());
                assertEquals(topic, messages.get(1).topicName());
                assertFalse(response.hasMore());

                verify(consumer).assign(Collections.singletonList(partition0));
                verify(consumer).seek(partition0, 0L);
                verify(consumerPool).returnObject(consumer);
        }

        @Test
        void getMessagesWithExecId_shouldFilterMessages() throws Exception {
                String topic = "test-topic-filter";
                Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
                Instant endTime = Instant.parse("2023-01-01T10:05:00Z");
                String targetExecId = "exec-123";
                TopicPartition partition0 = new TopicPartition(topic, 0);

                when(consumerPool.borrowObject()).thenReturn(consumer);
                when(consumer.partitionsFor(topic)).thenReturn(
                                Collections.singletonList(
                                                new org.apache.kafka.common.PartitionInfo(topic, 0, null, null, null)));

                // Mock offsets
                Map<TopicPartition, OffsetAndTimestamp> startOffsets = new HashMap<>();
                startOffsets.put(partition0, new OffsetAndTimestamp(0L, startTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == startTime.toEpochMilli())))
                                .thenReturn(startOffsets);

                Map<TopicPartition, OffsetAndTimestamp> endOffsets = new HashMap<>();
                endOffsets.put(partition0, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == endTime.toEpochMilli())))
                                .thenReturn(endOffsets);

                // Mock Avro keys
                String keySchemaString = "{\"type\":\"record\",\"name\":\"Key\",\"fields\":[{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"exec_id\",\"type\":\"string\"},{\"name\":\"timestamp\",\"type\":\"long\"}]}";
                Schema schema = new Schema.Parser().parse(keySchemaString);

                GenericRecord key1 = new GenericData.Record(schema);
                key1.put("version", "v1");
                key1.put("exec_id", targetExecId);
                key1.put("timestamp", 1000L);

                GenericRecord key2 = new GenericData.Record(schema);
                key2.put("version", "v1");
                key2.put("exec_id", "other-exec-id");
                key2.put("timestamp", 1001L);

                ConsumerRecord<Object, Object> record1 = new ConsumerRecord<>(topic, 0, 0L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, key1, "msg1",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                ConsumerRecord<Object, Object> record2 = new ConsumerRecord<>(topic, 0, 1L,
                                startTime.toEpochMilli() + 100,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, key2, "msg2",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(partition0, Arrays.asList(record1, record2));
                ConsumerRecords<Object, Object> records = new ConsumerRecords<>(recordsMap);

                when(consumer.poll(any())).thenReturn(records)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

                PaginatedResponse<MessageDto> response = kafkaMessageService.getMessagesWithExecId(topic, startTime,
                                endTime, targetExecId, null);
                List<MessageDto> messages = response.data();

                assertEquals(1, messages.size());
                assertEquals("msg1", messages.get(0).content());
                assertEquals(topic, messages.get(0).topicName());
                assertFalse(response.hasMore());

                verify(consumerPool).returnObject(consumer);
        }

        @Test
        void getMessagesFromTopics_shouldReturnMessagesFromMultipleTopics() throws Exception {
                String topic1 = "test-topic-1";
                String topic2 = "test-topic-2";
                List<String> topics = Arrays.asList(topic1, topic2);
                Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
                Instant endTime = Instant.parse("2023-01-01T10:05:00Z");

                TopicPartition partition1 = new TopicPartition(topic1, 0);
                TopicPartition partition2 = new TopicPartition(topic2, 0);

                when(consumerPool.borrowObject()).thenReturn(consumer);

                when(consumer.partitionsFor(topic1)).thenReturn(
                                Collections.singletonList(new org.apache.kafka.common.PartitionInfo(topic1, 0, null,
                                                null, null)));
                when(consumer.partitionsFor(topic2)).thenReturn(
                                Collections.singletonList(new org.apache.kafka.common.PartitionInfo(topic2, 0, null,
                                                null, null)));

                // Mock offsets
                Map<TopicPartition, OffsetAndTimestamp> startOffsets = new HashMap<>();
                startOffsets.put(partition1, new OffsetAndTimestamp(0L, startTime.toEpochMilli()));
                startOffsets.put(partition2, new OffsetAndTimestamp(0L, startTime.toEpochMilli()));

                when(consumer.offsetsForTimes(argThat(
                                map -> map != null && map.containsKey(partition1) && map.containsKey(partition2))))
                                .thenReturn(startOffsets);

                Map<TopicPartition, OffsetAndTimestamp> endOffsets = new HashMap<>();
                endOffsets.put(partition1, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));
                endOffsets.put(partition2, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));

                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition1) && map.containsKey(partition2)
                                                && map.get(partition1) == endTime.toEpochMilli())))
                                .thenReturn(endOffsets);

                // Mock poll
                ConsumerRecord<Object, Object> record1 = new ConsumerRecord<>(topic1, 0, 0L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg1",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record2 = new ConsumerRecord<>(topic2, 0, 0L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg2",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(partition1, Collections.singletonList(record1));
                recordsMap.put(partition2, Collections.singletonList(record2));
                ConsumerRecords<Object, Object> records = new ConsumerRecords<>(recordsMap);

                when(consumer.poll(any()))
                                .thenReturn(records)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()))
                                .thenReturn(records)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

                List<MessageDto> messages = kafkaMessageService.getMessagesFromTopics(topics, startTime, endTime, null);

                assertEquals(2, messages.size());
                // The order depends on how partitions are iterated, which is based on the list
                // order and partition assignment.
                // Since we use ArrayList to collect partitions, it should follow topic order.
                // But poll results might not guarantee order. Let's check content.

                List<String> contents = messages.stream().map(MessageDto::content).toList();
                List<String> topicNames = messages.stream().map(MessageDto::topicName).toList();

                // Check contains
                assertTrue(contents.contains("msg1"));
                assertTrue(contents.contains("msg2"));
                assertTrue(topicNames.contains(topic1));
                assertTrue(topicNames.contains(topic2));

                verify(consumer).assign(argThat(list -> list.contains(partition1) && list.contains(partition2)));
                verify(consumerPool).returnObject(consumer);
        }

        @Test
        void testGetMessagesForExecution_Success() throws Exception {
                String execId = "exec-1";
                String execTopic = "execids";
                String dataTopic = "data-topic";
                long startTs = 1000;
                long endTs = 2000;

                when(consumerPool.borrowObject()).thenReturn(consumer);

                // Mock finding execution times
                TopicPartition execPartition = new TopicPartition(execTopic, 0);

                // Mock records for scanning execids
                // Start record
                ConsumerRecord<Object, Object> startRecord = new ConsumerRecord<>(execTopic, 0, 0, startTs,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                                0, 0, execId, "start", new org.apache.kafka.common.header.internals.RecordHeaders(),
                                Optional.empty());

                // End record
                ConsumerRecord<Object, Object> endRecord = new ConsumerRecord<>(execTopic, 0, 1, endTs,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                                0, 0, execId, "end", new org.apache.kafka.common.header.internals.RecordHeaders(),
                                Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(execPartition, Arrays.asList(startRecord, endRecord));
                ConsumerRecords<Object, Object> execRecords = new ConsumerRecords<>(recordsMap);

                // Mock data retrieval
                TopicPartition dataPartition = new TopicPartition(dataTopic, 0);
                when(consumer.partitionsFor(dataTopic)).thenReturn(Collections.singletonList(
                                new org.apache.kafka.common.PartitionInfo(dataTopic, 0, null, null, null)));

                Map<TopicPartition, OffsetAndTimestamp> startOffsetsMap = new HashMap<>();
                startOffsetsMap.put(dataPartition, new OffsetAndTimestamp(10L, 1000L));
                when(consumer.offsetsForTimes(argThat(map -> map != null && map.containsValue(startTs))))
                                .thenReturn(startOffsetsMap);

                Map<TopicPartition, OffsetAndTimestamp> endOffsetsMap = new HashMap<>();
                endOffsetsMap.put(dataPartition, new OffsetAndTimestamp(20L, 1000L));
                when(consumer.offsetsForTimes(argThat(map -> map != null && map.containsValue(endTs))))
                                .thenReturn(endOffsetsMap);

                // Data records
                // Mock GenericRecord for Key
                GenericRecord mockKey = mock(GenericRecord.class);
                when(mockKey.get("exec_id")).thenReturn(execId);

                ConsumerRecord<Object, Object> dataRecord = new ConsumerRecord<>(dataTopic, 0, 15, 1500,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME,
                                0, 0, mockKey, "value", new org.apache.kafka.common.header.internals.RecordHeaders(),
                                Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> dataRecordsMap = new HashMap<>();
                dataRecordsMap.put(dataPartition, Collections.singletonList(dataRecord));
                ConsumerRecords<Object, Object> dataRecords = new ConsumerRecords<>(dataRecordsMap);

                // We need to chain the poll returns.
                // 1. Poll for exec scan
                // 2. Poll for data fetch
                // 3. Poll for data fetch (empty to stop)
                when(consumer.poll(any(Duration.class)))
                                .thenReturn(execRecords)
                                .thenReturn(dataRecords)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

                List<MessageDto> result = kafkaMessageService
                                .getMessagesForExecution(Collections.singletonList(dataTopic), execId);

                assertEquals(1, result.size());
                assertEquals("value", result.get(0).content());
                assertEquals(1500, result.get(0).timestamp());

                verify(consumerPool, times(2)).borrowObject();
                verify(consumerPool, times(2)).returnObject(consumer);
        }

        @Test
        void getMessages_shouldRespectMaxMessagesPerRequest() throws Exception {
                String topic = "test-topic";
                Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
                Instant endTime = Instant.parse("2023-01-01T10:05:00Z");
                TopicPartition partition0 = new TopicPartition(topic, 0);

                // Set a low limit to test enforcement
                when(kafkaProperties.getMaxMessagesPerRequest()).thenReturn(2);

                when(consumerPool.borrowObject()).thenReturn(consumer);
                when(consumer.partitionsFor(topic)).thenReturn(
                                Collections.singletonList(
                                                new org.apache.kafka.common.PartitionInfo(topic, 0, null, null, null)));

                // Mock offsetsForTimes
                Map<TopicPartition, OffsetAndTimestamp> startOffsets = new HashMap<>();
                startOffsets.put(partition0, new OffsetAndTimestamp(0L, startTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == startTime.toEpochMilli())))
                                .thenReturn(startOffsets);

                Map<TopicPartition, OffsetAndTimestamp> endOffsets = new HashMap<>();
                endOffsets.put(partition0, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == endTime.toEpochMilli())))
                                .thenReturn(endOffsets);

                // Mock poll - return 5 messages but expect only 2 to be collected
                ConsumerRecord<Object, Object> record1 = new ConsumerRecord<>(topic, 0, 0L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg1",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record2 = new ConsumerRecord<>(topic, 0, 1L,
                                startTime.toEpochMilli() + 1000,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg2",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record3 = new ConsumerRecord<>(topic, 0, 2L,
                                startTime.toEpochMilli() + 2000,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg3",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record4 = new ConsumerRecord<>(topic, 0, 3L,
                                startTime.toEpochMilli() + 3000,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg4",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record5 = new ConsumerRecord<>(topic, 0, 4L,
                                startTime.toEpochMilli() + 4000,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg5",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(partition0, Arrays.asList(record1, record2, record3, record4, record5));
                ConsumerRecords<Object, Object> records = new ConsumerRecords<>(recordsMap);

                when(consumer.poll(any())).thenReturn(records)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

                PaginatedResponse<MessageDto> response = kafkaMessageService.getMessages(topic, startTime,
                                endTime, null);
                List<MessageDto> messages = response.data();

                // Should only return 2 messages due to the limit
                assertEquals(2, messages.size());
                assertEquals("msg1", messages.get(0).content());
                assertEquals("msg2", messages.get(1).content());
                assertTrue(response.hasMore());
                assertNotNull(response.nextCursor());

                verify(consumer).assign(Collections.singletonList(partition0));
                verify(consumer).seek(partition0, 0L);
                verify(consumerPool).returnObject(consumer);
        }

        @Test
        void getMessages_shouldUseCursorWhenProvided() throws Exception {
                String topic = "test-topic";
                Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
                Instant endTime = Instant.parse("2023-01-01T10:05:00Z");
                TopicPartition partition0 = new TopicPartition(topic, 0);

                // Create a cursor pointing to offset 5
                Map<Integer, Long> cursorOffsets = new HashMap<>();
                cursorOffsets.put(0, 5L);
                String cursor = CursorUtil.createCursor(cursorOffsets);

                when(consumerPool.borrowObject()).thenReturn(consumer);
                when(consumer.partitionsFor(topic)).thenReturn(
                                Collections.singletonList(
                                                new org.apache.kafka.common.PartitionInfo(topic, 0, null, null, null)));

                // Mock end offsets
                Map<TopicPartition, OffsetAndTimestamp> endOffsets = new HashMap<>();
                endOffsets.put(partition0, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map != null && map.containsKey(partition0)
                                                && map.get(partition0) == endTime.toEpochMilli())))
                                .thenReturn(endOffsets);

                // Mock poll
                ConsumerRecord<Object, Object> record1 = new ConsumerRecord<>(topic, 0, 5L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg5",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(partition0, Arrays.asList(record1));
                ConsumerRecords<Object, Object> records = new ConsumerRecords<>(recordsMap);

                when(consumer.poll(any())).thenReturn(records)
                                .thenReturn(new ConsumerRecords<>(Collections.emptyMap()));

                PaginatedResponse<MessageDto> response = kafkaMessageService.getMessages(topic, startTime,
                                endTime, cursor);
                List<MessageDto> messages = response.data();

                assertEquals(1, messages.size());
                assertEquals("msg5", messages.get(0).content());
                assertEquals(5L, messages.get(0).offset());
                assertFalse(response.hasMore());

                verify(consumer).assign(Collections.singletonList(partition0));
                verify(consumer).seek(partition0, 5L); // Verify seek used cursor offset
                verify(consumerPool).returnObject(consumer);
        }

        @Test
        void getMessages_shouldSetHasMoreFlagCorrectly_whenMoreMessagesExist() throws Exception {
                String topic = "test-has-more";
                Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
                Instant endTime = Instant.parse("2023-01-01T10:05:00Z");
                TopicPartition partition0 = new TopicPartition(topic, 0);

                when(kafkaProperties.getMaxMessagesPerRequest()).thenReturn(1);
                when(consumerPool.borrowObject()).thenReturn(consumer);
                when(consumer.partitionsFor(topic)).thenReturn(
                                Collections.singletonList(
                                                new org.apache.kafka.common.PartitionInfo(topic, 0, null, null, null)));

                Map<TopicPartition, OffsetAndTimestamp> startOffsets = new HashMap<>();
                startOffsets.put(partition0, new OffsetAndTimestamp(0L, startTime.toEpochMilli()));
                when(consumer.offsetsForTimes(any())).thenReturn(startOffsets);

                Map<TopicPartition, OffsetAndTimestamp> endOffsets = new HashMap<>();
                endOffsets.put(partition0, new OffsetAndTimestamp(10L, endTime.toEpochMilli()));
                when(consumer.offsetsForTimes(
                                argThat(map -> map.get(partition0) == endTime.toEpochMilli())))
                                .thenReturn(endOffsets);

                ConsumerRecord<Object, Object> record1 = new ConsumerRecord<>(topic, 0, 0L, startTime.toEpochMilli(),
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg1",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());
                ConsumerRecord<Object, Object> record2 = new ConsumerRecord<>(topic, 0, 1L,
                                startTime.toEpochMilli() + 1000,
                                org.apache.kafka.common.record.TimestampType.CREATE_TIME, 0, 0, "key", "msg2",
                                new org.apache.kafka.common.header.internals.RecordHeaders(), Optional.empty());

                Map<TopicPartition, List<ConsumerRecord<Object, Object>>> recordsMap = new HashMap<>();
                recordsMap.put(partition0, Arrays.asList(record1, record2));
                ConsumerRecords<Object, Object> records = new ConsumerRecords<>(recordsMap);

                when(consumer.poll(any())).thenReturn(records);

                PaginatedResponse<MessageDto> response = kafkaMessageService.getMessages(topic, startTime,
                                endTime, null);

                assertEquals(1, response.data().size());
                assertTrue(response.hasMore());
                assertNotNull(response.nextCursor());
        }
}
