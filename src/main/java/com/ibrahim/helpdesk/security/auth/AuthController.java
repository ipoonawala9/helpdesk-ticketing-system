package com.ibrahim.helpdesk.security.auth;

import com.ibrahim.helpdesk.security.dto.ChangePasswordRequest;
import com.ibrahim.helpdesk.security.dto.LoginRequest;
import com.ibrahim.helpdesk.security.dto.LoginResponse;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Authentication", description = "Log in and read your own profile")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Exchange email and password for an access token")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Change the authenticated user's own password")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, @CurrentUserId Long userId) {
        authService.changePassword(userId, request);
    }

    /** The authenticated user's own profile. */
    @Operation(summary = "Get the authenticated user's own profile")
    @GetMapping("/me")
    public UserResponse me(@CurrentUserId Long userId) {
        return userService.getUserById(userId, userId);
    }
}
