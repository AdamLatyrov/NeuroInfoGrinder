package com.larbcorp.neuroinfogrinder.security;

import com.larbcorp.neuroinfogrinder.domain.users.UserService;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userService.authenticateOrBootstrap(request.username(), request.password());

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return ResponseEntity.ok(new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }

        UserEntity user = userService.getCurrentUser(userId);
        return ResponseEntity.ok(new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole()
        ));
    }
}
