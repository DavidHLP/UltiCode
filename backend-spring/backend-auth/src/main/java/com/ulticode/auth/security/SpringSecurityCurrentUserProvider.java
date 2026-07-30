package com.ulticode.auth.security;

import com.ulticode.common.auth.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** Spring Security context adapter for the canonical identity seam. */
@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String getCurrentUserId() {
        Authentication authentication = authentication();
        return isAuthenticated(authentication) ? authentication.getName() : null;
    }

    @Override
    public String getCurrentUsername() {
        Authentication authentication = authentication();
        if (!isAuthenticated(authentication) || authentication.getDetails() == null) {
            return null;
        }
        return authentication.getDetails().toString();
    }

    @Override
    public boolean isAuthenticated() {
        return isAuthenticated(authentication());
    }

    @Override
    public boolean hasRole(String role) {
        Authentication authentication = authentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    @Override
    public boolean hasAnyRole(String... roles) {
        Authentication authentication = authentication();
        if (authentication == null || roles == null || roles.length == 0) {
            return false;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        for (String role : roles) {
            if (authorities.contains("ROLE_" + role)) {
                return true;
            }
        }
        return false;
    }

    private Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}
