package com.ulticode.auth.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;

/**
 * JWT authentication filter that extracts and validates JWT tokens from requests.
 * Tokens can be provided via HTTP-only cookies (primary) or Authorization header (fallback).
 * Extends OncePerRequestFilter to ensure single execution per request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Extract token from cookie or Authorization header
        String token = extractToken(request);

        if (token != null) {
            try {
                Claims claims = jwtTokenProvider.parseToken(token);
                if (claims != null && !"refresh".equals(claims.get("type", String.class))) {
                    // Extract user information from the already verified claims
                    String userId = claims.getSubject();
                    String username = claims.get("username", String.class);
                    String role = claims.get("role", String.class);

                    if (userId != null && username != null) {
                        // Create UserDetails with userId as principal
                        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                        UserDetails userDetails = new User(
                                userId,  // Use userId as username/principal
                                "",      // Password not needed for JWT auth
                                Collections.singletonList(authority)
                        );

                        // Create authentication token with username in details
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                        // Store username in details for later access
                        ((UsernamePasswordAuthenticationToken) authentication)
                                .setDetails(username);

                        // Set authentication in security context
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("Authenticated user: {} (userId: {}, role: {})", username, userId, role);
                    }
                }
            } catch (ExpiredJwtException e) {
                log.debug("JWT token expired during authentication: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            } catch (MalformedJwtException | SignatureException | UnsupportedJwtException | IllegalArgumentException e) {
                log.error("Invalid JWT token during authentication: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from request.
     * First tries to get token from cookie (primary method),
     * then falls back to Authorization header (Bearer token).
     *
     * @param request the HTTP request
     * @return the JWT token, or null if not found
     */
    private String extractToken(HttpServletRequest request) {
        // Primary: Try to get token from cookie
        String token = extractTokenFromCookie(request);
        if (token != null) {
            return token;
        }

        // Fallback: Try to get token from Authorization header
        return extractTokenFromHeader(request);
    }

    /**
     * Extract JWT token from HTTP-only cookie.
     *
     * @param request the HTTP request
     * @return the JWT token from cookie, or null if not found
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        String cookieName = jwtProperties.getCookie().getAccessToken().getName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    /**
     * Extract JWT token from Authorization header (Bearer token).
     *
     * @param request the HTTP request
     * @return the JWT token from header, or null if not found
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
