package com.larbcorp.neuroinfogrinder.domain.questions;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.CreatePromptRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TestPromptRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TestPromptResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdatePromptRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptRepository promptRepository;

    @Transactional(readOnly = true)
    public List<PromptEntity> getAll() {
        return promptRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PromptEntity getById(Long id) {
        return promptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Prompt not found: " + id));
    }

    @Transactional
    public PromptEntity create(CreatePromptRequest request) {
        PromptEntity entity = new PromptEntity();
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setContent(request.content());
        entity.setVariablesJson(request.variables());
        entity.setVersion(request.version() != null ? request.version() : "1.0");
        entity.setStatus("DRAFT");
        return promptRepository.save(entity);
    }

    @Transactional
    public PromptEntity update(Long id, UpdatePromptRequest request) {
        PromptEntity entity = getById(id);
        if (request.name() != null) entity.setName(request.name());
        if (request.type() != null) entity.setType(request.type());
        if (request.content() != null) entity.setContent(request.content());
        if (request.variables() != null) entity.setVariablesJson(request.variables());
        if (request.version() != null) entity.setVersion(request.version());
        if (request.status() != null) {
            if ("ACTIVE".equals(request.status())) {
                promptRepository.findByTypeAndStatus(entity.getType(), "ACTIVE").stream()
                    .filter(prompt -> !prompt.getId().equals(entity.getId()))
                    .forEach(prompt -> prompt.setStatus("DRAFT"));
            }
            entity.setStatus(request.status());
        }
        return promptRepository.save(entity);
    }

    public TestPromptResponse testPrompt(Long id, TestPromptRequest request) {
        PromptEntity prompt = getById(id);

        // Placeholder: simulate prompt execution
        String content = prompt.getContent();
        if (request.variables() != null) {
            for (var entry : request.variables().entrySet()) {
                content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }

        int estimatedInputTokens = content.length() / 4;
        int estimatedOutputTokens = 200;
        int totalTokens = estimatedInputTokens + estimatedOutputTokens;
        double estimatedCost = totalTokens * 0.00002;

        return new TestPromptResponse(
            "Placeholder output for prompt: " + prompt.getName(),
            estimatedInputTokens,
            estimatedOutputTokens,
            totalTokens,
            estimatedCost
        );
    }

    @Transactional
    public void delete(Long id) {
        if (!promptRepository.existsById(id)) {
            throw new IllegalArgumentException("Prompt not found: " + id);
        }
        promptRepository.deleteById(id);
    }

    public List<String> parseVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return List.of();
        }
        return Arrays.stream(variablesJson.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
