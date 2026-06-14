package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.questions.PromptService;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.CreatePromptRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.PromptResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TestPromptRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TestPromptResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdatePromptRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public List<PromptResponse> getAll() {
        return promptService.getAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromptResponse create(@Valid @RequestBody CreatePromptRequest request) {
        return toResponse(promptService.create(request));
    }

    @PutMapping("/{id}")
    public PromptResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdatePromptRequest request) {
        return toResponse(promptService.update(id, request));
    }

    @PostMapping("/{id}/test")
    public TestPromptResponse testPrompt(@PathVariable Long id,
                                          @Valid @RequestBody TestPromptRequest request) {
        return promptService.testPrompt(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        promptService.delete(id);
    }

    private PromptResponse toResponse(PromptEntity entity) {
        return new PromptResponse(
            entity.getId(),
            entity.getName(),
            entity.getType(),
            entity.getVersion(),
            entity.getContent(),
            promptService.parseVariables(entity.getVariablesJson()),
            entity.getStatus(),
            List.of(),
            List.of()
        );
    }
}
