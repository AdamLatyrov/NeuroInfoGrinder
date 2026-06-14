package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideSummaryResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GuideEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GuideMapper {

    @Mapping(target = "groupTitle", expression = "java(null)")
    @Mapping(target = "tags", expression = "java(java.util.List.of())")
    GuideSummaryResponse toResponse(GuideEntity entity);
}
