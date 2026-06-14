package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.PromptResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.PromptEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PromptMapper {

    @Mapping(target = "variables", source = "variablesJson")
    @Mapping(target = "linkedClassifiers", ignore = true)
    @Mapping(target = "linkedRules", ignore = true)
    PromptResponse toResponse(PromptEntity entity);

    default List<String> mapVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(variablesJson.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
