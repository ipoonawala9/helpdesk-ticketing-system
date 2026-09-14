package com.ibrahim.helpdesk.user.controller;

import com.ibrahim.helpdesk.common.paging.PageRequests;
import com.ibrahim.helpdesk.common.paging.PageResponse;
import com.ibrahim.helpdesk.security.auth.CurrentUserId;
import com.ibrahim.helpdesk.user.dto.CreateUserRequest;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.entity.UserRole;
import com.ibrahim.helpdesk.user.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Users", description = "User accounts; created by administrators")
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Which roles and organization an admin may create is enforced in UserService. */
    @Operation(summary = "Create a user; organization admins may create only agents and customers of their own organization")
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
    @Operation(summary = "List users an administrator may see, with filters, search, sorting and paging")
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORG_ADMIN')")
    public PageResponse<UserResponse> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort,
            @CurrentUserId Long currentUserId) {

        return userService.listUsers(currentUserId, role, organizationId, active, q,
                PageRequests.of(page, size, sort, UserService.SORTABLE_FIELDS));
    }

    @Operation(summary = "Get one user within the caller's scope")
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id, @CurrentUserId Long currentUserId) {
        return userService.getUserById(id, currentUserId);
    }
}
