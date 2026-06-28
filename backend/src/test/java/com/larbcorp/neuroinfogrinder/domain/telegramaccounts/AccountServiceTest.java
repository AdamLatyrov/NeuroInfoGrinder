package com.larbcorp.neuroinfogrinder.domain.telegramaccounts;

import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitCodeRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.TelegramAccountEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.GroupRepository;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.TelegramAccountRepository;
import com.larbcorp.neuroinfogrinder.telegram.TelegramTdlibService;
import com.larbcorp.neuroinfogrinder.telegram.model.TelegramAuthStateResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    @Test
    void getAllReturnsOnlyAccountsForOwner() {
        TelegramAccountRepository accountRepository = mock(TelegramAccountRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        AccountService service = new AccountService(accountRepository, groupRepository, telegramTdlibService);

        TelegramAccountEntity account = account(10L, 1L, "+100000000");
        when(accountRepository.findByOwnerUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(account));
        when(groupRepository.countByAccountIdAndOwnerUserId(10L, 1L)).thenReturn(3L);

        var response = service.getAll(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(10L);
        assertThat(response.get(0).groupsCount()).isEqualTo(3);
        verify(accountRepository).findByOwnerUserIdOrderByCreatedAtAsc(1L);
    }

    @Test
    void submitCodeRejectsAccountOwnedByAnotherUser() {
        TelegramAccountRepository accountRepository = mock(TelegramAccountRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        AccountService service = new AccountService(accountRepository, groupRepository, telegramTdlibService);

        when(accountRepository.findByIdAndOwnerUserId(70L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitCode(1L, 70L, new SubmitCodeRequest("12345")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Account not found");

        verifyNoInteractions(telegramTdlibService);
    }

    @Test
    void submitCodeUsesAccountSpecificTdlibClient() {
        TelegramAccountRepository accountRepository = mock(TelegramAccountRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        TelegramTdlibService telegramTdlibService = mock(TelegramTdlibService.class);
        AccountService service = new AccountService(accountRepository, groupRepository, telegramTdlibService);

        TelegramAccountEntity account = account(10L, 1L, "+100000000");
        when(accountRepository.findByIdAndOwnerUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(telegramTdlibService.submitCode(10L, "12345"))
            .thenReturn(new TelegramAuthStateResponse("ready", true, false, false, false, null));
        when(accountRepository.save(account)).thenReturn(account);
        when(groupRepository.countByAccountIdAndOwnerUserId(10L, 1L)).thenReturn(0L);

        var response = service.submitCode(1L, 10L, new SubmitCodeRequest("12345"));

        assertThat(response.status()).isEqualTo("CONNECTED");
        verify(telegramTdlibService).submitCode(10L, "12345");
    }

    private TelegramAccountEntity account(Long id, Long ownerUserId, String phone) {
        TelegramAccountEntity account = new TelegramAccountEntity();
        account.setId(id);
        account.setOwnerUserId(ownerUserId);
        account.setPhone(phone);
        account.setStatus("CONNECTED");
        return account;
    }
}
