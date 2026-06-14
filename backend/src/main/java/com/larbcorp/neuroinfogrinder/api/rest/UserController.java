package com.larbcorp.neuroinfogrinder.api.rest;

import com.larbcorp.neuroinfogrinder.domain.users.UserService;
import com.larbcorp.neuroinfogrinder.domain.users.dto.ChangeUserPasswordRequest;
import com.larbcorp.neuroinfogrinder.domain.users.dto.CreateUserRequest;
import com.larbcorp.neuroinfogrinder.domain.users.dto.UpdateUserHiddenRequest;
import com.larbcorp.neuroinfogrinder.domain.users.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PatchMapping("/{id}/hidden")
    public UserResponse updateHidden(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserHiddenRequest request) {
        return userService.updateHidden(id, request, currentUserId());
    }

    @PatchMapping("/{id}/password")
    public UserResponse changePassword(@PathVariable Long id,
                                       @Valid @RequestBody ChangeUserPasswordRequest request) {
        return userService.changePassword(id, request);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userId;
    }
}
