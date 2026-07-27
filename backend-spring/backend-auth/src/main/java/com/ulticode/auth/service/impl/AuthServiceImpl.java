package com.ulticode.auth.service.impl;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.dto.RegisterDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.service.AuthService;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.util.UuidGenerator;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Implementation of AuthService inside backend-auth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthAccountPort accountPort;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionPort authSessionPort;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    @Override
    public LoginResponse login(LoginDTO loginDTO, HttpServletResponse response) {
        AuthAccountRecord account = accountPort.findByUsername(loginDTO.getUsername()).orElse(null);

        if (account == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(loginDTO.getPassword(), account.password())) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        ensureAccountActive(account);
        accountPort.updateLastLoginAt(account.id());

        return authSessionPort.completeLogin(account, response);
    }

    @Override
    public LoginResponse register(RegisterDTO registerDTO, HttpServletResponse response) {
        boolean usernameTaken = accountPort.findByUsername(registerDTO.getUsername()).isPresent();
        if (usernameTaken) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USERNAME_TAKEN);
        }

        if (registerDTO.getEmail() != null && !registerDTO.getEmail().isBlank()) {
            if (accountPort.findByEmail(registerDTO.getEmail()).isPresent()) {
                throw new AuthBusinessException(AuthErrorCode.AUTH_EMAIL_TAKEN);
            }
        }

        AuthAccountRecord newAccount = new AuthAccountRecord(
                uuidGenerator.newId(),
                registerDTO.getUsername().trim(),
                registerDTO.getEmail() != null ? registerDTO.getEmail().trim() : null,
                passwordEncoder.encode(registerDTO.getPassword()),
                "USER",
                true,
                false,
                null,
                LocalDateTime.now(clock)
        );

        AuthAccountRecord created = accountPort.create(newAccount);
        log.info("Registered new user: {}", created.username());

        accountPort.updateLastLoginAt(created.id());

        return authSessionPort.completeLogin(created, response);
    }

    @Override
    public LoginResponse refresh(String refreshToken, HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED, "Refresh token is missing");
        }

        RefreshTokenService.RotationResult rotation;
        try {
            rotation = refreshTokenService.validateAndRotate(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED, "Refresh token has expired");
        }

        AuthAccountRecord account = accountPort.findById(rotation.userId()).orElse(null);
        if (account == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }

        ensureAccountActive(account);

        return authSessionPort.completeRefresh(account, rotation.token(), response);
    }

    @Override
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokePresentedToken(refreshToken);
        }
        authSessionPort.clearSession(response);
    }

    private void ensureAccountActive(AuthAccountRecord account) {
        if (!Boolean.TRUE.equals(account.isActive())) {
            log.warn("Authentication attempt on inactive account: {}", account.username());
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (Boolean.TRUE.equals(account.isBanned())) {
            if (account.bannedUntil() == null || account.bannedUntil().isAfter(LocalDateTime.now(clock))) {
                log.warn("Authentication attempt on banned account: {}", account.username());
                throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
            }
        }
    }
}
