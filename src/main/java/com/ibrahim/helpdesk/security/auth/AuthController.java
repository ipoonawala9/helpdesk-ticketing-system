package com.ibrahim.helpdesk.security.auth;

import com.ibrahim.helpdesk.security.dto.LoginRequest;
import com.ibrahim.helpdesk.security.dto.LoginResponse;
import com.ibrahim.helpdesk.user.dto.UserResponse;
import com.ibrahim.helpdesk.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** The authenticated user's own profile. */
    @GetMapping("/me")
    public UserResponse me(@CurrentUserId Long userId) {
        return userService.getUserById(userId, userId);
    }
}
