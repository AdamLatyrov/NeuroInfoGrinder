package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.questions.MonitorService;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.GroupStatsResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.GuidesByGroupResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.QueueHistoryItem;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.QueueStatusResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.StuckTaskResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TokenByProviderDto;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TokenDailyResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TokenSummaryResponse;
import com.larbcorp.neuroinfogrinder.domain.questions.dto.TopMessagesGroupResponse;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/tokens/summary")
    public TokenSummaryResponse getTokenSummary() {
        return monitorService.getTokenSummary();
    }

    @GetMapping("/tokens/daily")
    public List<TokenDailyResponse> getTokenDaily(
        @RequestParam String from,
        @RequestParam String to
    ) {
        return monitorService.getTokenDaily(from, to);
    }

    @GetMapping("/tokens/by-provider")
    public List<TokenByProviderDto> getTokensByProvider() {
        return monitorService.getTokensByProvider();
    }

    @GetMapping(value = "/tokens/export", produces = "text/csv")
    public String exportTokensCsv() {
        return monitorService.exportTokensCsv();
    }

    @GetMapping("/queue/status")
    public QueueStatusResponse getQueueStatus() {
        return monitorService.getQueueStatus();
    }

    @GetMapping("/queue/history")
    public PageResponse<QueueHistoryItem> getQueueHistory(Pageable pageable) {
        return PageResponse.from(
            monitorService.getQueueHistory(pageable).map(entity ->
                new QueueHistoryItem(
                    entity.getId(),
                    entity.getTaskType(),
                    entity.getStatus(),
                    entity.getCreatedAt(),
                    entity.getStartedAt(),
                    entity.getCompletedAt(),
                    entity.getErrorMessage()
                )
            )
        );
    }

    @GetMapping("/queue/stuck")
    public List<StuckTaskResponse> getStuckTasks() {
        return monitorService.getStuckTasks().stream()
            .map(entity -> new StuckTaskResponse(
                entity.getId(),
                entity.getTaskType(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
            ))
            .toList();
    }

    @PostMapping("/queue/pause")
    @ResponseStatus(HttpStatus.OK)
    public void pauseQueue() {
        monitorService.pauseQueue();
    }

    @PostMapping("/queue/resume")
    @ResponseStatus(HttpStatus.OK)
    public void resumeQueue() {
        monitorService.resumeQueue();
    }

    @DeleteMapping("/queue/clear")
    @ResponseStatus(HttpStatus.OK)
    public void clearQueue() {
        monitorService.clearQueue();
    }

    @GetMapping("/groups/stats")
    public List<GroupStatsResponse> getGroupStats() {
        return monitorService.getGroupStats();
    }

    @GetMapping("/groups/top-messages")
    public List<TopMessagesGroupResponse> getTopMessagesGroups(
        @RequestParam(defaultValue = "10") int limit
    ) {
        return monitorService.getTopMessagesGroups(limit);
    }

    @GetMapping("/groups/guides-by-group")
    public List<GuidesByGroupResponse> getGuidesByGroup() {
        return monitorService.getGuidesByGroup();
    }
}
