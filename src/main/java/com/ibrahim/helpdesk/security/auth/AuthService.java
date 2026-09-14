package com.ibrahim.helpdesk.security.auth;

import com.ibrahim.helpdesk.security.dto.LoginRequest;
import com.ibrahim.helpdesk.security.dto.LoginResponse;
import com.ibrahim.helpdesk.security.jwt.JwtTokenService;
import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.mapper.UserMapper;
import com.ibrahim.helpdesk.user.service.UserService;
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
    private final JwtTokenService tokenService;

    /**
     * Verifies email and password and issues an access token. Every failure,
     * including an unknown email or an inactive account, produces the same
     * error, and Spring Security performs a password hash comparison even for
     * unknown emails so response time does not reveal which emails exist.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }

        User user = userService.findOrThrow(Long.parseLong(authentication.getName()));
        JwtTokenService.IssuedToken token = tokenService.issue(user);

        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserMapper.toResponse(user));
    }
}
