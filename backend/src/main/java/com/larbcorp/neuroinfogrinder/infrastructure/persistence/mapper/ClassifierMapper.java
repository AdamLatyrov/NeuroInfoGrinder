package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.ClassifierResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.ClassifierEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClassifierMapper {

    @Mapping(target = "providerName", ignore = true)
    @Mapping(target = "regex", source = "regexPattern")
    @Mapping(target = "modelConfig", source = "modelConfigJson")
    @Mapping(target = "rulesCount", ignore = true)
    ClassifierResponse toResponse(ClassifierEntity entity);
}
