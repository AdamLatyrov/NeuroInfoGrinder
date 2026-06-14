package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.AccountService;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.AccountResponse;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.CreateAccountRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitCodeRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.SubmitPasswordRequest;
import com.larbcorp.neuroinfogrinder.domain.telegramaccounts.dto.UpdateProxyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public List<AccountResponse> getAll() {
        return accountService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(request);
    }

    @PostMapping("/{id}/code")
    public AccountResponse submitCode(@PathVariable Long id, @Valid @RequestBody SubmitCodeRequest request) {
        return accountService.submitCode(id, request);
    }

    @PostMapping("/{id}/password")
    public AccountResponse submitPassword(@PathVariable Long id, @Valid @RequestBody SubmitPasswordRequest request) {
        return accountService.submitPassword(id, request);
    }

    @PatchMapping("/{id}/proxy")
    public AccountResponse updateProxy(@PathVariable Long id, @Valid @RequestBody UpdateProxyRequest request) {
        return accountService.updateProxy(id, request);
    }

    @PostMapping("/{id}/reconnect")
    public AccountResponse reconnect(@PathVariable Long id) {
        return accountService.reconnect(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        accountService.delete(id);
    }
}
