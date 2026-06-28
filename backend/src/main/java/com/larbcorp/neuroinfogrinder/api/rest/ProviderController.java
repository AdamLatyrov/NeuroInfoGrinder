package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.questions.ProviderService;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.CreateProviderRequest;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.ProviderResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TestProviderResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.UpdateProviderRequest;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.AiProviderEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<ProviderResponse> getAll() {
        return providerService.getAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse create(@Valid @RequestBody CreateProviderRequest request) {
        return toResponse(providerService.create(request));
    }

    @PutMapping("/{id}")
    public ProviderResponse update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateProviderRequest request) {
        return toResponse(providerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        providerService.delete(id);
    }

    @PostMapping("/{id}/test")
    public TestProviderResponse testConnection(@PathVariable Long id) {
        return providerService.testConnection(id);
    }

    @PostMapping("/{id}/activate")
    public ProviderResponse activate(@PathVariable Long id) {
        return toResponse(providerService.activate(id));
    }

    private ProviderResponse toResponse(AiProviderEntity entity) {
        return new ProviderResponse(
            entity.getId(),
            entity.getName(),
            entity.getProtocol(),
            entity.getEndpointUrl(),
            entity.getApiKeyEncrypted() != null && !entity.getApiKeyEncrypted().isBlank(),
            entity.getModel(),
            entity.getStatus(),
            providerService.isActive(entity.getId()),
            entity.getLastTestedAt(),
            entity.getLastTestResult(),
            entity.getLastError()
        );
    }
}
