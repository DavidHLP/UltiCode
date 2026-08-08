package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.util.UuidGenerator;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Default HTTP-neutral implementation of {@link AuthenticationWorkflow}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthenticationWorkflow implements AuthenticationWorkflow {
    private final AuthAccountPort accountPort;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionPort authSessionPort;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    @Override
    public AuthSession login(String username, String password) {
        AuthAccountRecord account = accountPort.findByUsername(username).orElse(null);

        if (account == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, account.password())) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        ensureAccountActive(account);
        accountPort.updateLastLoginAt(account.id());

        return authSessionPort.completeLogin(account);
    }

    @Override
    public AuthSession register(String username, String email, String password) {
        boolean usernameTaken = accountPort.findByUsername(username).isPresent();
        if (usernameTaken) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USERNAME_TAKEN);
        }

        if (email != null && !email.isBlank()) {
            if (accountPort.findByEmail(email).isPresent()) {
                throw new AuthBusinessException(AuthErrorCode.AUTH_EMAIL_TAKEN);
            }
        }

        AuthAccountRecord newAccount = new AuthAccountRecord(
                uuidGenerator.newId(),
                username.trim(),
                email != null ? email.trim() : null,
                passwordEncoder.encode(password),
                "USER",
                true,
                false,
                null,
                LocalDateTime.now(clock)
        );

        AuthAccountRecord created = accountPort.create(newAccount);
        log.info("Registered new user: {}", created.username());

        accountPort.updateLastLoginAt(created.id());

        return authSessionPort.completeLogin(created);
    }

    @Override
    public AuthSession refresh(String refreshToken) {
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

        return authSessionPort.completeRefresh(account, rotation.token());
    }

    @Override
    public AuthSession logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokePresentedToken(refreshToken);
        }
        return authSessionPort.clearSession();
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
