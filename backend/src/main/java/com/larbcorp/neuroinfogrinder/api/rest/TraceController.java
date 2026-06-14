package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.findings.PipelineTraceService;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.FlowMetricsResponse;
import com.larbcorp.neuroinfogrinder.domain.findings.dto.PipelineTraceResponse;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
public class TraceController {

    private final PipelineTraceService pipelineTraceService;

    @GetMapping("/{traceId}")
    public List<PipelineTraceResponse> getTrace(@PathVariable String traceId) {
        return pipelineTraceService.getTrace(traceId);
    }

    @GetMapping("/message/{messageId}")
    public List<PipelineTraceResponse> getTraceByMessage(@PathVariable Long messageId) {
        return pipelineTraceService.getTraceByMessage(messageId);
    }

    @GetMapping
    public PageResponse<PipelineTraceResponse> getTraces(
            @RequestParam Instant from,
            @RequestParam Instant to,
            Pageable pageable) {
        Page<PipelineTraceResponse> page = pipelineTraceService.getTraces(from, to, pageable)
            .map(pipelineTraceService::toResponse);
        return PageResponse.from(page);
    }

    @GetMapping("/metrics")
    public FlowMetricsResponse getFlowMetrics() {
        return pipelineTraceService.getFlowMetrics();
    }
}
