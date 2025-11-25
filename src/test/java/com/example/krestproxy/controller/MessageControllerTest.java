package com.example.krestproxy.controller;

import com.example.krestproxy.dto.PaginatedResponse;
import com.example.krestproxy.service.KafkaMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private KafkaMessageService kafkaMessageService;

        @Test
        void getMessages_shouldReturnUnauthorized_whenApiKeyIsMissing() throws Exception {
                mockMvc.perform(get("/api/v1/messages/test-topic")
                                .param("startTime", "2023-01-01T10:00:00Z")
                                .param("endTime", "2023-01-01T10:05:00Z"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void getMessages_shouldReturnUnauthorized_whenApiKeyIsInvalid() throws Exception {
                mockMvc.perform(get("/api/v1/messages/test-topic")
                                .header("X-API-KEY", "invalid-key")
                                .param("startTime", "2023-01-01T10:00:00Z")
                                .param("endTime", "2023-01-01T10:05:00Z"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void getMessages_shouldReturnMessages_whenApiKeyIsValid() throws Exception {
                com.example.krestproxy.dto.MessageDto msg1 = new com.example.krestproxy.dto.MessageDto("test-topic",
                                "msg1", 1000L, 0, 0L);
                com.example.krestproxy.dto.MessageDto msg2 = new com.example.krestproxy.dto.MessageDto("test-topic",
                                "msg2", 2000L, 0, 1L);

                when(kafkaMessageService.getMessages(eq("test-topic"), any(Instant.class), any(Instant.class),
                                isNull()))
                                .thenReturn(new PaginatedResponse<>(Arrays.asList(msg1, msg2), null, false));

                mockMvc.perform(get("/api/v1/messages/test-topic")
                                .header("X-API-KEY", "secret-api-key")
                                .param("startTime", "2023-01-01T10:00:00Z")
                                .param("endTime", "2023-01-01T10:05:00Z"))
                                .andExpect(status().isOk())
                                .andExpect(content().json(
                                                "{\"data\":[{\"topicName\":\"test-topic\",\"content\":\"msg1\",\"timestamp\":1000,\"partition\":0,\"offset\":0},{\"topicName\":\"test-topic\",\"content\":\"msg2\",\"timestamp\":2000,\"partition\":0,\"offset\":1}],\"nextCursor\":null,\"hasMore\":false}"));
        }

        @Test
        void getMessagesWithExecId_shouldReturnMessages_whenApiKeyIsValid() throws Exception {
                com.example.krestproxy.dto.MessageDto msg1 = new com.example.krestproxy.dto.MessageDto("test-topic",
                                "msg1", 1000L, 0, 0L);

                when(kafkaMessageService.getMessagesWithExecId(eq("test-topic"), any(Instant.class), any(Instant.class),
                                eq("exec-1"), isNull()))
                                .thenReturn(new PaginatedResponse<>(Collections.singletonList(msg1), null, false));

                mockMvc.perform(get("/api/v1/messages/test-topic/filter")
                                .header("X-API-KEY", "secret-api-key")
                                .param("startTime", "2023-01-01T10:00:00Z")
                                .param("endTime", "2023-01-01T10:05:00Z")
                                .param("execId", "exec-1"))
                                .andExpect(status().isOk())
                                .andExpect(content().json(
                                                "{\"data\":[{\"topicName\":\"test-topic\",\"content\":\"msg1\",\"timestamp\":1000,\"partition\":0,\"offset\":0}],\"nextCursor\":null,\"hasMore\":false}"));
        }
}
