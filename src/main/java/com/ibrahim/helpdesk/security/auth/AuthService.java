package com.ibrahim.helpdesk.security.auth;

import com.ibrahim.helpdesk.security.dto.LoginRequest;
import com.ibrahim.helpdesk.security.dto.LoginResponse;
import com.ibrahim.helpdesk.security.jwt.JwtTokenService;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.mapper.UserMapper;
import com.ibrahim.helpdesk.user.service.UserService;
import com.ibrahim.helpdesk.exception.FieldValidationException;
import com.ibrahim.helpdesk.security.dto.ChangePasswordRequest;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtTokenService tokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verifies email and password and issues an access token. Every failure,
     * including an unknown email or an inactive account, produces the same
     * error, and Spring Security performs a password hash comparison even for
     * unknown emails so response time does not reveal which emails exist.
     * Repeated wrong passwords for one email block sign-in for it for a while.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        loginAttemptLimiter.checkAllowed(request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (AuthenticationException e) {
            loginAttemptLimiter.recordFailure(request.email());
            throw new InvalidCredentialsException();
        }
        loginAttemptLimiter.recordSuccess(request.email());

        User user = userService.findOrThrow(Long.parseLong(authentication.getName()));
        JwtTokenService.IssuedToken token = tokenService.issue(user);

        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserMapper.toResponse(user));
    }

    /**
     * Replaces the signed-in user's password after checking the current one.
     * Wrong current passwords count towards the same limit as sign-in, so a
     * stolen access token cannot be used to guess the password.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userService.findOrThrow(userId);
        loginAttemptLimiter.checkAllowed(user.getEmail());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            loginAttemptLimiter.recordFailure(user.getEmail());
            throw new FieldValidationException("currentPassword", "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new FieldValidationException("newPassword", "New password must be different from the current one");
        }
        loginAttemptLimiter.recordSuccess(user.getEmail());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
