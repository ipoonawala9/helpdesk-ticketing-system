package com.ibrahim.helpdesk.user.controller;

import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Which roles and organization an admin may create is enforced in UserService. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public UserResponse createUser(
            @Valid @RequestBody CreateUserRequest request,
            @CurrentUserId Long currentUserId) {

        return userService.createUser(request, currentUserId);
    }

    /**
     * Users within the caller's scope, e.g. {@code ?role=SUPPORT_AGENT} for the
     * agents an organization administrator can assign.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public List<UserResponse> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Long organizationId,
            @CurrentUserId Long currentUserId) {

        return userService.listUsers(currentUserId, role, organizationId);
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return userService.getUserById(id, currentUserId);
    }
}
