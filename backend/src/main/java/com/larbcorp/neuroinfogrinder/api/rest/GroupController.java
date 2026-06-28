package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.chats.GroupService;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.BulkAssignRequest;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.BulkToggleRequest;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.GroupResponse;
import com.larbcorp.neuroinfogrinder.domain.chats.dto.UpdateGroupRequest;
import com.larbcorp.neuroinfogrinder.shared.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public PageResponse<GroupResponse> getGroups(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long accountId,
            @AuthenticationPrincipal Long ownerUserId,
            Pageable pageable
    ) {
        return groupService.getGroups(ownerUserId, search, status, enabled, accountId, pageable);
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    public void syncGroups(@AuthenticationPrincipal Long ownerUserId) {
        groupService.syncGroups(ownerUserId);
    }

    @PatchMapping("/{id}")
    public GroupResponse updateGroup(@AuthenticationPrincipal Long ownerUserId,
                                     @PathVariable Long id,
                                     @Valid @RequestBody UpdateGroupRequest request) {
        return groupService.updateGroup(ownerUserId, id, request);
    }

    @PostMapping("/bulk-toggle")
    @ResponseStatus(HttpStatus.OK)
    public void bulkToggle(@AuthenticationPrincipal Long ownerUserId,
                           @Valid @RequestBody BulkToggleRequest request) {
        groupService.bulkToggle(ownerUserId, request);
    }

    @PostMapping("/bulk-assign")
    @ResponseStatus(HttpStatus.OK)
    public void bulkAssign(@AuthenticationPrincipal Long ownerUserId,
                           @Valid @RequestBody BulkAssignRequest request) {
        groupService.bulkAssign(ownerUserId, request);
    }
}
