package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.GuidePublicationService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationLogResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationResultResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationSendSelectedRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuidePublicationSettingsResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuidePublicationSettingsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/guide-publication")
public class GuidePublicationController {

    private final GuidePublicationService guidePublicationService;

    public GuidePublicationController(GuidePublicationService guidePublicationService) {
        this.guidePublicationService = guidePublicationService;
    }

    @GetMapping("/settings")
    public GuidePublicationSettingsResponse getSettings(@AuthenticationPrincipal Long ownerUserId) {
        return guidePublicationService.getSettingsResponse(ownerUserId);
    }

    @PutMapping("/settings")
    public GuidePublicationSettingsResponse updateSettings(
        @AuthenticationPrincipal Long ownerUserId,
        @Valid @RequestBody UpdateGuidePublicationSettingsRequest request
    ) {
        return guidePublicationService.updateSettings(ownerUserId, request);
    }

    @PostMapping("/send/{guideId}")
    public GuidePublicationResultResponse sendGuide(
        @AuthenticationPrincipal Long ownerUserId,
        @PathVariable Long guideId,
        @RequestParam(required = false) Long targetGroupId,
        @RequestParam(required = false) Long topicId,
        @RequestParam(defaultValue = "false") boolean force
    ) {
        return guidePublicationService.sendGuideViaTelegramAccount(ownerUserId, guideId, targetGroupId, topicId, force);
    }

    @PostMapping("/send-selected")
    public GuidePublicationResultResponse sendSelected(
        @AuthenticationPrincipal Long ownerUserId,
        @Valid @RequestBody GuidePublicationSendSelectedRequest request
    ) {
        return guidePublicationService.sendSelected(
            ownerUserId,
            request.guideIds(),
            Boolean.TRUE.equals(request.force()),
            Boolean.TRUE.equals(request.dryRun())
        );
    }

    @PostMapping("/send-all-unsent")
    public GuidePublicationResultResponse sendAllUnsent(
        @AuthenticationPrincipal Long ownerUserId,
        @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
        @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return guidePublicationService.sendAllUnsent(ownerUserId, limit, dryRun);
    }

    @PostMapping("/test")
    public GuidePublicationResultResponse sendTest(@AuthenticationPrincipal Long ownerUserId) {
        return guidePublicationService.sendTest(ownerUserId);
    }

    @GetMapping("/logs")
    public List<GuidePublicationLogResponse> getLogs(@AuthenticationPrincipal Long ownerUserId,
                                                     @RequestParam(required = false) Long guideId) {
        return guidePublicationService.getLogs(ownerUserId, guideId);
    }
}
