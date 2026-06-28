package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.AccountService;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.AccountResponse;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.CreateAccountRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitCodeRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitPasswordRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.UpdateProxyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> getAll(@AuthenticationPrincipal Long ownerUserId) {
        return accountService.getAll(ownerUserId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal Long ownerUserId,
                                  @Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(ownerUserId, request);
    }

    @PostMapping("/{id}/code")
    public AccountResponse submitCode(@AuthenticationPrincipal Long ownerUserId,
                                      @PathVariable Long id,
                                      @Valid @RequestBody SubmitCodeRequest request) {
        return accountService.submitCode(ownerUserId, id, request);
    }

    @PostMapping("/{id}/password")
    public AccountResponse submitPassword(@AuthenticationPrincipal Long ownerUserId,
                                          @PathVariable Long id,
                                          @Valid @RequestBody SubmitPasswordRequest request) {
        return accountService.submitPassword(ownerUserId, id, request);
    }

    @PatchMapping("/{id}/proxy")
    public AccountResponse updateProxy(@AuthenticationPrincipal Long ownerUserId,
                                       @PathVariable Long id,
                                       @Valid @RequestBody UpdateProxyRequest request) {
        return accountService.updateProxy(ownerUserId, id, request);
    }

    @PostMapping("/{id}/reconnect")
    public AccountResponse reconnect(@AuthenticationPrincipal Long ownerUserId,
                                     @PathVariable Long id) {
        return accountService.reconnect(ownerUserId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long ownerUserId,
                       @PathVariable Long id) {
        accountService.delete(ownerUserId, id);
    }
}
