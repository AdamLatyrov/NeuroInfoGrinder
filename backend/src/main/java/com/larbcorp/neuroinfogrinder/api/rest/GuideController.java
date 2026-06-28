package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.GuideService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.BulkDeleteGuidesRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideDetailResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideRegenerateResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideSummaryResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuideContentRequest;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.UpdateGuideStatusRequest;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/guides")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping
    public PageResponse<GuideSummaryResponse> getGuides(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long groupId,
        @RequestParam(required = false) Long providerId,
        @RequestParam(required = false) String classifier,
        @RequestParam(required = false) Double minConfidence,
        @RequestParam(required = false) Double maxCost,
        @RequestParam(required = false) Integer minUsefulness,
        @RequestParam(required = false) Integer maxUsefulness,
        @AuthenticationPrincipal Long ownerUserId,
        Pageable pageable
    ) {
        Integer normalizedMinUsefulness = clampUsefulness(minUsefulness);
        Integer normalizedMaxUsefulness = clampUsefulness(maxUsefulness);
        if (normalizedMinUsefulness != null
            && normalizedMaxUsefulness != null
            && normalizedMinUsefulness > normalizedMaxUsefulness) {
            throw new IllegalArgumentException("minUsefulness must be less than or equal to maxUsefulness");
        }
        Page<GuideSummaryResponse> page = guideService
            .getGuides(
                ownerUserId,
                status,
                groupId,
                providerId,
                classifier,
                minConfidence,
                maxCost,
                normalizedMinUsefulness,
                normalizedMaxUsefulness,
                pageable
            )
            .map(guideService::toSummaryResponse);
        return PageResponse.from(page);
    }

    @GetMapping("/{id}")
    public GuideDetailResponse getGuideDetail(@AuthenticationPrincipal Long ownerUserId,
                                              @PathVariable Long id) {
        return guideService.getGuideDetail(ownerUserId, id);
    }

    @PostMapping("/{id}/regenerate")
    public GuideRegenerateResponse regenerate(@AuthenticationPrincipal Long ownerUserId,
                                              @PathVariable Long id) {
        return guideService.regenerate(ownerUserId, id);
    }

    @PatchMapping("/{id}/status")
    public GuideSummaryResponse updateStatus(@AuthenticationPrincipal Long ownerUserId,
                                              @PathVariable Long id,
                                              @Valid @RequestBody UpdateGuideStatusRequest request) {
        return guideService.toSummaryResponse(guideService.updateStatus(ownerUserId, id, request.status()));
    }

    @PutMapping("/{id}/content")
    public GuideSummaryResponse updateContent(@AuthenticationPrincipal Long ownerUserId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody UpdateGuideContentRequest request) {
        return guideService.toSummaryResponse(guideService.updateContent(ownerUserId, id, request));
    }

    @PostMapping("/{id}/mark-not-duplicate")
    public GuideSummaryResponse markNotDuplicate(@AuthenticationPrincipal Long ownerUserId,
                                                 @PathVariable Long id) {
        return guideService.toSummaryResponse(guideService.markNotDuplicate(ownerUserId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long ownerUserId,
                       @PathVariable Long id) {
        guideService.delete(ownerUserId, id);
    }

    @PostMapping("/bulk-delete")
    public BulkDeleteGuidesResponse bulkDelete(@AuthenticationPrincipal Long ownerUserId,
                                               @Valid @RequestBody BulkDeleteGuidesRequest request) {
        return new BulkDeleteGuidesResponse(guideService.bulkDelete(ownerUserId, request.ids()));
    }

    public record BulkDeleteGuidesResponse(int deleted) {}

    private Integer clampUsefulness(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, Math.min(100, value));
    }
}
