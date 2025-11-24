package com.example.krestproxy.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ApiGatlingSimulation extends Simulation {

    // Configuration via System properties
    String baseUrl = System.getProperty("baseUrl", "https://localhost:8443");
    String apiKey = System.getProperty("apiKey", "default-api-key");
    String topic = System.getProperty("topic", "test-topic");
    String execId = System.getProperty("execId", "test-exec-id");

    // Default time window: last 1 hour
    String startTime = System.getProperty("startTime", Instant.now().minus(1, ChronoUnit.HOURS).toString());
    String endTime = System.getProperty("endTime", Instant.now().toString());

    // HTTP Protocol Configuration
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(baseUrl)
        .header("X-API-KEY", apiKey)
        .acceptHeader("application/json")
        .disableWarmUp(); // Disable warm up to avoid initial request if not needed

    // Scenario 1: Get Messages from a Topic
    ScenarioBuilder scnGetMessages = scenario("Get Messages")
        .exec(
            http("Get Messages")
                .get("/api/v1/messages/" + topic)
                .queryParam("startTime", startTime)
                .queryParam("endTime", endTime)
                .check(status().is(200))
        );

    // Scenario 2: Get Messages from a Topic with Execution ID
    ScenarioBuilder scnGetMessagesWithFilter = scenario("Get Messages Filtered by ExecId")
        .exec(
            http("Get Messages Filtered")
                .get("/api/v1/messages/" + topic + "/filter")
                .queryParam("startTime", startTime)
                .queryParam("endTime", endTime)
                .queryParam("execId", execId)
                .check(status().is(200))
        );

    // Scenario 3: Get Messages from Multiple Topics (using single topic for simplicity in default)
    ScenarioBuilder scnGetMessagesFromTopics = scenario("Get Messages From Topics")
        .exec(
            http("Get Messages From Topics")
                .get("/api/v1/messages/filter")
                .queryParam("topics", topic)
                .queryParam("startTime", startTime)
                .queryParam("endTime", endTime)
                .queryParam("execId", execId)
                .check(status().is(200))
        );

    // Scenario 4: Get Messages By Execution (across topics)
    ScenarioBuilder scnGetMessagesByExecution = scenario("Get Messages By Execution")
        .exec(
            http("Get Messages By Execution")
                .get("/api/v1/messages/by-execution")
                .queryParam("topics", topic)
                .queryParam("execId", execId)
                .check(status().is(200))
        );

    {
        setUp(
            scnGetMessages.injectOpen(rampUsers(10).during(10)),
            scnGetMessagesWithFilter.injectOpen(rampUsers(10).during(10)),
            scnGetMessagesFromTopics.injectOpen(rampUsers(10).during(10)),
            scnGetMessagesByExecution.injectOpen(rampUsers(10).during(10))
        ).protocols(httpProtocol);
    }
}
