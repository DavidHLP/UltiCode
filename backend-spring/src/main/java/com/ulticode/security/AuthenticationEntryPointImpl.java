package com.ulticode.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom implementation of AuthenticationEntryPoint.
 * Handles authentication failures by returning a JSON error response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Handle authentication failure by returning a JSON error response.
     *
     * @param request       the HTTP request
     * @param response      the HTTP response
     * @param authException the authentication exception
     * @throws IOException if writing response fails
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        log.debug("Authentication failed for request {}: {}",
                request.getRequestURI(), authException.getMessage());

        // Set response properties
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Create error response using Result class
        Result<Void> errorResult = Result.error(
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getMessage()
        );

        // Write JSON response
        objectMapper.writeValue(response.getWriter(), errorResult);
    }
}
