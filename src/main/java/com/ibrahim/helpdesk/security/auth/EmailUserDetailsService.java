package com.ibrahim.helpdesk.security.auth;

import com.ibrahim.helpdesk.user.entity.User;
import com.ibrahim.helpdesk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

/**
 * Looks users up by email for password login. The username in Spring
 * Security's model is the user's id, so a successful login identifies exactly
 * one account.
 */
@RequiredArgsConstructor
public class EmailUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailIgnoreCase(email == null ? "" : email.strip())
                .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));

        return org.springframework.security.core.userdetails.User
                .withUsername(String.valueOf(user.getId()))
                .password(user.getPassword() == null ? "" : user.getPassword())
                .disabled(!Boolean.TRUE.equals(user.getActive()))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
