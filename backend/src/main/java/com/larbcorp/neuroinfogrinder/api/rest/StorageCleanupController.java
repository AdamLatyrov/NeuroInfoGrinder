package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.storage.StorageCleanupService;
import com.larbcorp.neuroinfogrinder.domain.storage.dto.StorageCleanupRequest;
import com.larbcorp.neuroinfogrinder.domain.storage.dto.StorageCleanupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageCleanupController {

    private final StorageCleanupService storageCleanupService;

    @PostMapping("/cleanup")
    public StorageCleanupResponse cleanup(
        @AuthenticationPrincipal Long ownerUserId,
        @Valid @RequestBody StorageCleanupRequest request
    ) {
        return storageCleanupService.cleanup(ownerUserId, request.scope(), request.confirmation());
    }
}
