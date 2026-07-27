package com.ulticode.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Authentication entry point for the auth service.
 *
 * <p>Mirrors {@code com.ulticode.security.AuthenticationEntryPointImpl} in
 * backend-legacy but is intentionally a private copy inside backend-auth:
 * the auth service does not depend on backend-legacy, and the global
 * cross-cutting entry point remains where it is until Phase 4 cutover.
 * The {@code UNAUTHORIZED} code and message byte values are kept
 * identical to the legacy HTTP envelope (see
 * {@code ErrorCodeDelegationTest} in backend-legacy).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        log.debug(
                "Authentication failed for request {}: {}",
                request.getRequestURI(),
                authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Result<Void> errorResult =
                Result.error(BaseErrorCode.UNAUTHORIZED.code(), BaseErrorCode.UNAUTHORIZED.message());
        objectMapper.writeValue(response.getWriter(), errorResult);
    }
}
