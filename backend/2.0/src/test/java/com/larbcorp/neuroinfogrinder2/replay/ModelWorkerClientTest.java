package com.larbcorp.neuroinfogrinder2.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ModelWorkerClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void classifySendsItemsRequestBody() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/classify", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"model":"BOOTSTRAP_BERT_CLASSIFIER","status":"SUCCESS","results":[],"latencyMs":1}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            ModelWorkerClient client = new ModelWorkerClient(objectMapper, baseUrl, Duration.ofSeconds(5).toMillis());

            client.classify(List.of(new ModelWorkerClient.WorkerItem(
                    "message-1",
                    "How to configure BGE embeddings?",
                    objectMapper.createObjectNode().put("isQuestion", true)
            )));

            assertThat(capturedBody.get()).isNotNull();
            assertThat(objectMapper.readTree(capturedBody.get()).path("items")).hasSize(1);
        } finally {
            server.stop(0);
        }
    }
}
