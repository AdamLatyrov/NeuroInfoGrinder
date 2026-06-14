package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.questions.dto.ProviderResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProviderMapper {

    @Mapping(target = "hasApiKey", source = "apiKeyEncrypted")
    ProviderResponse toResponse(AiProviderEntity entity);

    default boolean mapHasApiKey(String apiKeyEncrypted) {
        return apiKeyEncrypted != null;
    }
}
