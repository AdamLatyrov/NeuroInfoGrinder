package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineTraceService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.FlowMetricsResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.PipelineTraceResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.PipelineTuningCaseResponse;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
public class TraceController {

    private final PipelineTraceService pipelineTraceService;

    @GetMapping("/{traceId}")
    public List<PipelineTraceResponse> getTrace(@AuthenticationPrincipal Long ownerUserId,
                                                @PathVariable String traceId) {
        return pipelineTraceService.getTrace(ownerUserId, traceId);
    }

    @GetMapping("/message/{messageId}")
    public List<PipelineTraceResponse> getTraceByMessage(@AuthenticationPrincipal Long ownerUserId,
                                                         @PathVariable Long messageId) {
        return pipelineTraceService.getTraceByMessage(ownerUserId, messageId);
    }

    @GetMapping
    public PageResponse<PipelineTraceResponse> getTraces(
            @AuthenticationPrincipal Long ownerUserId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            Pageable pageable) {
        Page<PipelineTraceResponse> page = pipelineTraceService.getTraces(ownerUserId, from, to, pageable)
            .map(pipelineTraceService::toResponse);
        return PageResponse.from(page);
    }

    @GetMapping("/tuning-cases")
    public PageResponse<PipelineTuningCaseResponse> getTuningCases(
            @AuthenticationPrincipal Long ownerUserId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long classifierId,
            @RequestParam(required = false) Long promptId,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(defaultValue = "true") boolean problemOnly,
            Pageable pageable) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(Duration.ofDays(7));
        Page<PipelineTuningCaseResponse> page = pipelineTraceService.getTuningCases(
            ownerUserId,
            resolvedFrom,
            resolvedTo,
            stage,
            status,
            classifierId,
            promptId,
            ruleId,
            groupId,
            problemOnly,
            pageable
        );
        return PageResponse.from(page);
    }

    @GetMapping(value = "/tuning-cases/export", produces = "application/x-ndjson")
    public ResponseEntity<String> exportTuningCases(
            @AuthenticationPrincipal Long ownerUserId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long classifierId,
            @RequestParam(required = false) Long promptId,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(defaultValue = "true") boolean problemOnly,
            @RequestParam(defaultValue = "200") int limit) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(Duration.ofDays(7));
        String body = pipelineTraceService.exportTuningCases(
            ownerUserId,
            resolvedFrom,
            resolvedTo,
            stage,
            status,
            classifierId,
            promptId,
            ruleId,
            groupId,
            problemOnly,
            limit
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-ndjson"))
            .body(body);
    }

    @GetMapping("/metrics")
    public FlowMetricsResponse getFlowMetrics(@AuthenticationPrincipal Long ownerUserId) {
        return pipelineTraceService.getFlowMetrics(ownerUserId);
    }
}
