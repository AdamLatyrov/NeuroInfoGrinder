package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.RuleActionDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.RuleConditionDto;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.RuleResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.RuleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface RuleMapper {

    @Mapping(target = "order", source = "ruleOrder")
    @Mapping(target = "conditions", source = "conditionsJson")
    @Mapping(target = "actions", source = "actionsJson")
    RuleResponse toResponse(RuleEntity entity);

    default List<RuleConditionDto> mapConditions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    default List<RuleActionDto> mapActions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    default String mapConditionsToJson(List<RuleConditionDto> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(conditions);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    default String mapActionsToJson(List<RuleActionDto> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            return new ObjectMapper().writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
