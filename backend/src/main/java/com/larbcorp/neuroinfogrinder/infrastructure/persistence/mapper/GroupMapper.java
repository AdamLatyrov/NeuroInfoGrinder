package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.chats.dto.GroupResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.GroupEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(target = "messagesPerDay", ignore = true)
    @Mapping(target = "guidesFound", ignore = true)
    GroupResponse toResponse(GroupEntity entity);
}
