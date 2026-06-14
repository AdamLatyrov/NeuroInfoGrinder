package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.messages.MessageService;
import com.larbcorp.neuroinfogrinder.domain.messages.TelegramRefreshCoordinator;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.EnqueueRequest;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.MessageChainResponse;
import com.larbcorp.neuroinfogrinder.domain.messages.dto.MessageResponse;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/groups/{groupId}/messages")
public class MessageController {

    private final MessageService messageService;
    private final TelegramRefreshCoordinator telegramRefreshCoordinator;

    public MessageController(MessageService messageService,
                             TelegramRefreshCoordinator telegramRefreshCoordinator) {
        this.messageService = messageService;
        this.telegramRefreshCoordinator = telegramRefreshCoordinator;
    }

    @GetMapping
    public PageResponse<MessageResponse> getMessages(
            @PathVariable Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long topicId,
            Pageable pageable
    ) {
        return messageService.getMessages(groupId, status, topicId, pageable);
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    public void syncMessages(@PathVariable Long groupId) {
        telegramRefreshCoordinator.requestMessageSync(groupId, "manual_or_ui_request");
    }

    @GetMapping("/{messageId}/chain")
    public MessageChainResponse getMessageChain(
            @PathVariable Long groupId,
            @PathVariable Long messageId
    ) {
        return messageService.getMessageChain(groupId, messageId);
    }

    @GetMapping("/{messageId}")
    public MessageResponse getMessage(
            @PathVariable Long groupId,
            @PathVariable Long messageId
    ) {
        return messageService.getMessage(groupId, messageId);
    }

    @PostMapping("/{messageId}/enqueue")
    @ResponseStatus(HttpStatus.OK)
    public void enqueueForProcessing(
            @PathVariable Long groupId,
            @PathVariable Long messageId,
            @RequestBody(required = false) EnqueueRequest request
    ) {
        EnqueueRequest effectiveRequest = request != null ? request : new EnqueueRequest();
        messageService.enqueueForProcessing(groupId, messageId, effectiveRequest);
    }
}
