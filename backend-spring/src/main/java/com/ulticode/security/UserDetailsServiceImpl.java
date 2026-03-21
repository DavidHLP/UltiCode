package com.ulticode.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Implementation of Spring Security's UserDetailsService.
 * Provides user authentication data for Spring Security.
 * <p>
 * TODO: This is a placeholder implementation. Actual user loading
 * will be implemented in Phase 2 when the User entity and repository
 * are available.
 */
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * Load user by username (or user ID in our case).
     * <p>
     * TODO: Replace with actual user lookup from database.
     * This placeholder always throws UsernameNotFoundException.
     *
     * @param username the username or user ID
     * @return UserDetails for the user
     * @throws UsernameNotFoundException always in this placeholder
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("loadUserByUsername called with: {}", username);

        // TODO: Implement actual user loading in Phase 2
        // Example implementation:
        // UserEntity user = userRepository.findByUsername(username)
        //     .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        // return User.builder()
        //     .username(user.getId().toString())
        //     .password(user.getPassword())
        //     .authorities(Collections.singletonList(
        //         new SimpleGrantedAuthority("ROLE_" + user.getRole())
        //     ))
        //     .build();

        throw new UsernameNotFoundException("User service not yet implemented. Will be available in Phase 2.");
    }
}
