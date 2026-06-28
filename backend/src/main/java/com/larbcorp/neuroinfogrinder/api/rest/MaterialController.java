package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.GuideService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideDetailResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.GuideSummaryResponse;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final GuideService guideService;

    public MaterialController(GuideService guideService) {
        this.guideService = guideService;
    }

    @GetMapping
    public PageResponse<GuideSummaryResponse> getMaterials(
        @RequestParam(required = false) String contentType,
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
            .getMaterials(
                ownerUserId,
                contentType,
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
    public GuideDetailResponse getMaterialDetail(@AuthenticationPrincipal Long ownerUserId,
                                                 @PathVariable Long id) {
        return guideService.getMaterialDetail(ownerUserId, id);
    }

    private Integer clampUsefulness(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, Math.min(100, value));
    }
}
