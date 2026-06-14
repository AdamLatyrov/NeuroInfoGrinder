package com.larbcorp.neuroinfogrinder.infrastructure.persistence.mapper;

import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.AccountResponse;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.AccountResponse.ProxyInfo;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "proxy", source = ".")
    @Mapping(target = "groupsCount", ignore = true)
    @Mapping(target = "tokensUsed", ignore = true)
    @Mapping(target = "lastActivityAt", source = "lastSyncAt")
    AccountResponse toResponse(TelegramAccountEntity entity);

    default ProxyInfo map(TelegramAccountEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ProxyInfo(
                entity.getProxyType(),
                entity.getProxyHost(),
                entity.getProxyPort(),
                entity.getProxyUsername(),
                entity.getProxyPasswordEncrypted() != null
        );
    }
}
