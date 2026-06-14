package com.larbcorp.neuroinfogrinder.api.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.RuleService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.CreateRuleRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.ReorderRulesRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.RuleActionDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.RuleConditionDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.RuleResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateRuleRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.RuleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<RuleResponse> getAll() {
        return ruleService.getAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(@RequestBody CreateRuleRequest request) {
        return toResponse(ruleService.create(request));
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @RequestBody UpdateRuleRequest request) {
        return toResponse(ruleService.update(id, request));
    }

    @PatchMapping("/reorder")
    @ResponseStatus(HttpStatus.OK)
    public void reorder(@RequestBody ReorderRulesRequest request) {
        ruleService.reorder(request);
    }

    @PatchMapping("/{id}/status")
    public RuleResponse updateStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return toResponse(ruleService.updateStatus(id, request.get("status")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ruleService.delete(id);
    }

    private RuleResponse toResponse(RuleEntity entity) {
        List<RuleConditionDto> conditions = parseJson(entity.getConditionsJson(),
            new TypeReference<List<RuleConditionDto>>() {});
        List<RuleActionDto> actions = parseJson(entity.getActionsJson(),
            new TypeReference<List<RuleActionDto>>() {});

        return new RuleResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getActionType(),
            entity.getRuleOrder(),
            conditions,
            actions,
            entity.getStatus()
        );
    }

    private <T> List<T> parseJson(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
