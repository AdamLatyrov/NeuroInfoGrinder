package com.larbcorp.neuroinfogrinder.domain.users;

import com.larbcorp.neuroinfogrinder.domain.users.dto.ChangeUserPasswordRequest;
import com.larbcorp.neuroinfogrinder.domain.users.dto.CreateUserRequest;
import com.larbcorp.neuroinfogrinder.domain.users.dto.UpdateUserHiddenRequest;
import com.larbcorp.neuroinfogrinder.domain.users.dto.UserResponse;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.entity.UserEntity;
import com.larbcorp.neuroinfogrinder.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_LANGUAGE_CODE = "ru";
    private static final String DEFAULT_TIMEZONE = "Europe/Moscow";
    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserEntity authenticateOrBootstrap(String rawUsername, String rawPassword) {
        String username = normalizeUsername(rawUsername);

        if (userRepository.countByHiddenFalseAndPasswordIsNotNull() == 0) {
            UserEntity bootstrapUser = userRepository.findByUsernameIgnoreCase(username)
                    .orElseGet(UserEntity::new);
            bootstrapUser.setUsername(username);
            bootstrapUser.setPassword(passwordEncoder.encode(rawPassword));
            bootstrapUser.setRole(ADMIN_ROLE);
            bootstrapUser.setHidden(false);
            ensureDefaults(bootstrapUser);
            return userRepository.save(bootstrapUser);
        }

        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> !Boolean.TRUE.equals(existing.getHidden()))
                .orElseThrow(() -> invalidCredentials());

        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw invalidCredentials();
        }

        if (!ADMIN_ROLE.equals(user.getRole())) {
            user.setRole(ADMIN_ROLE);
            user = userRepository.save(user);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserEntity getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getHidden()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAllByOrderByHiddenAscUsernameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already exists");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(ADMIN_ROLE);
        user.setHidden(false);
        ensureDefaults(user);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateHidden(Long userId, UpdateUserHiddenRequest request, Long currentUserId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean hideUser = Boolean.TRUE.equals(request.hidden());
        if (hideUser && Objects.equals(user.getId(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot hide the current user");
        }
        if (hideUser
                && user.getPassword() != null
                && userRepository.countByHiddenFalseAndPasswordIsNotNull() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one active login must remain");
        }

        user.setHidden(hideUser);
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse changePassword(Long userId, ChangeUserPasswordRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPassword(passwordEncoder.encode(request.password()));
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getHidden(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private void ensureDefaults(UserEntity user) {
        if (user.getLanguageCode() == null || user.getLanguageCode().isBlank()) {
            user.setLanguageCode(DEFAULT_LANGUAGE_CODE);
        }
        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            user.setTimezone(DEFAULT_TIMEZONE);
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
