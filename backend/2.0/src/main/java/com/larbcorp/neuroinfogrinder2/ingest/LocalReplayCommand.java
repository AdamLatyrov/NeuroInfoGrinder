package com.larbcorp.neuroinfogrinder2.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class LocalReplayCommand implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalReplayCommand.class);

    private final ObjectMapper objectMapper;
    private final TelegramIngestionService ingestionService;
    private final ConfigurableApplicationContext applicationContext;

    public LocalReplayCommand(
            ObjectMapper objectMapper,
            TelegramIngestionService ingestionService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.objectMapper = objectMapper;
        this.ingestionService = ingestionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.getNonOptionArgs().contains("import-local-messages")) {
            return;
        }

        List<String> files = args.getOptionValues("file");
        if (files == null || files.isEmpty() || files.get(0).isBlank()) {
            throw new IllegalArgumentException("Use import-local-messages --file path/to/messages.jsonl");
        }

        int imported = importFile(Path.of(files.get(0)));
        log.info("local replay imported messages={}", imported);
        applicationContext.close();
    }

    public int importFile(Path file) throws IOException {
        int imported = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                ingestionService.ingestLocalMessage(toInput(node));
                imported++;
            }
        }
        return imported;
    }

    private LocalMessageInput toInput(JsonNode node) {
        return new LocalMessageInput(
                nullableLong(node, "accountId"),
                requiredLong(node, "telegramChatId"),
                requiredLong(node, "telegramMessageId"),
                nullableLong(node, "telegramTopicId"),
                nullableLong(node, "senderId"),
                text(node, "senderName"),
                text(node, "senderUsername"),
                node.path("senderIsBot").isBoolean() ? node.path("senderIsBot").asBoolean() : false,
                nullableLong(node, "replyToMessageId"),
                text(node, "text"),
                text(node, "caption"),
                text(node, "messageDate"),
                text(node, "editDate"),
                text(node, "contentType"),
                jsonArray(node.path("entities")),
                jsonArray(node.path("captionEntities")),
                node.path("media"),
                node
        );
    }

    private List<JsonNode> jsonArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        for (JsonNode value : node) {
            values.add(value);
        }
        return values;
    }

    private Long requiredLong(JsonNode node, String field) {
        Long value = nullableLong(node, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
