package com.ulticode.notification.security;

import com.ulticode.common.auth.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class NotificationCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String getCurrentUserId() {
        Authentication authentication = authentication();
        return authenticated(authentication) ? authentication.getName() : null;
    }

    @Override
    public String getCurrentUsername() {
        Authentication authentication = authentication();
        return authenticated(authentication) && authentication.getDetails() != null
                ? authentication.getDetails().toString() : null;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated(authentication());
    }

    @Override
    public boolean hasRole(String role) {
        return authentication() != null && authentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    @Override
    public boolean hasAnyRole(String... roles) {
        Authentication authentication = authentication();
        return authentication != null && roles != null
                && Arrays.stream(roles).anyMatch(this::hasRole);
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String principal
                && "anonymousUser".equals(principal));
    }
}
