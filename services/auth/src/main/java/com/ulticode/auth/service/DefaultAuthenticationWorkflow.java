package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.util.UuidGenerator;
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
        return login(username, password, false);
    }

    @Override
    public AuthSession login(String username, String password, boolean adminOnly) {
        AuthAccountRecord account = accountPort.findByUsername(username).orElse(null);

        if (account == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, account.password())) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        return accountPort.withLockedAccount(account.id(), current -> {
            AuthAccountRecord admitted = current.filter(candidate -> account.password().equals(candidate.password()))
                    .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
            ensureAccountActive(admitted);
            requireAllowedRole(admitted, adminOnly);
            accountPort.updateLastLoginAt(admitted.id());
            return authSessionPort.completeLogin(admitted);
        });
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

        String accountId = refreshTokenService.requireVerifiedUserId(refreshToken);
        return accountPort.withLockedAccount(accountId,
                current -> rotateAndIssueSession(refreshToken, current.orElse(null)));
    }

    @Override
    public AuthSession switchAccount(String accountId, String refreshToken, boolean adminOnly) {
        if (!accountId.equals(refreshTokenService.verifiedUserId(refreshToken))) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
        return accountPort.withLockedAccount(accountId, current -> {
            AuthAccountRecord account = current
                    .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED));
            requireAllowedRole(account, adminOnly);
            return rotateAndIssueSession(refreshToken, account);
        });
    }

    private AuthSession rotateAndIssueSession(String refreshToken, AuthAccountRecord account) {
        String rotatedToken = refreshTokenService.validateAndRotate(refreshToken, () -> {
            if (account == null) {
                throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
            }
            ensureAccountActive(account);
        });
        return authSessionPort.completeRefresh(account, rotatedToken);
    }

    @Override
    public AuthSession logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokePresentedToken(refreshToken);
        }
        return authSessionPort.clearSession();
    }

    private static void requireAllowedRole(AuthAccountRecord account, boolean adminOnly) {
        if (adminOnly && !"ADMIN".equals(account.role()) && !"SUPER_ADMIN".equals(account.role())) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
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
