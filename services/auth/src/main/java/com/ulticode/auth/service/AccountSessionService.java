package com.ulticode.auth.service;

import static com.ulticode.websecurity.csrf.CookieCsrfFilter.REFRESH_TOKEN_COOKIE;
import static com.ulticode.websecurity.csrf.CookieCsrfFilter.SAVED_ACCOUNT_COOKIE_PREFIX;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.refreshtoken.service.RefreshTokenService;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.CookieMutation;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Moves a refresh credential between the active session and HttpOnly saved-account cookies. */
@Service
@RequiredArgsConstructor
public class AccountSessionService {
    public static final int MAX_ACCOUNTS = 5;
    private final AuthenticationWorkflow authenticationWorkflow;
    private final RefreshTokenService refreshTokenService;
    private final AuthAccountPort accountPort;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public record Account(String id, String name, String email, String role, boolean current) {}

    public List<Account> list(Map<String, String> cookies) {
        String current = refreshTokenService.findValidUserId(cookies.get(REFRESH_TOKEN_COOKIE));
        Map<String, Account> accounts = new LinkedHashMap<>();
        addAccount(accounts, current, true);
        cookies.forEach((name, token) -> {
            if (name.startsWith(SAVED_ACCOUNT_COOKIE_PREFIX)) {
                String id = refreshTokenService.findValidUserId(token);
                if (id != null && name.equals(cookieName(id))) {
                    addAccount(accounts, id, id.equals(current));
                }
            }
        });
        return List.copyOf(accounts.values());
    }

    public AuthSession add(String username, String password, Map<String, String> cookies, boolean adminOnly) {
        List<Account> accounts = list(cookies);
        if (accounts.size() >= MAX_ACCOUNTS && accounts.stream().noneMatch(a -> a.name().equals(username))) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_REQUEST, "At most five accounts can be signed in");
        }
        AuthSession session = authenticationWorkflow.login(username, password);
        requireAllowedRole(session.response().getUser().role(), adminOnly);
        return transfer(session, cookies);
    }

    public AuthSession switchTo(String accountId, Map<String, String> cookies, boolean adminOnly) {
        String token = cookies.get(cookieName(accountId));
        if (!accountId.equals(refreshTokenService.verifiedUserId(token))) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
        }
        requireAllowedRole(accountPort.findById(accountId).orElseThrow(
                () -> new AuthBusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED)).role(), adminOnly);
        // Rotation and the active/banned account checks remain in the existing workflow.
        return transfer(authenticationWorkflow.refresh(token), cookies);
    }

    private static void requireAllowedRole(String role, boolean adminOnly) {
        if (adminOnly && !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }
    }

    private AuthSession transfer(AuthSession session, Map<String, String> cookies) {
        String targetId = session.response().getUser().id();
        String previousToken = cookies.get(REFRESH_TOKEN_COOKIE);
        String previousId = refreshTokenService.findValidUserId(previousToken);
        List<CookieMutation> mutations = new ArrayList<>(session.cookies());
        if (previousId != null && !previousId.equals(targetId)) {
            mutations.add(cookie(previousId, previousToken, jwtProperties.getCookie().getRefreshToken().getMaxAge()));
        } else if (previousToken != null) {
            refreshTokenService.revokePresentedToken(previousToken);
        }
        String savedTarget = cookies.get(cookieName(targetId));
        if (savedTarget != null) {
            refreshTokenService.revokePresentedToken(savedTarget);
            mutations.add(cookie(targetId, "", 0));
        }
        return new AuthSession(session.response(), mutations);
    }

    private void addAccount(Map<String, Account> accounts, String id, boolean current) {
        if (id == null) return;
        AuthAccountRecord account = accountPort.findById(id).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.isActive())
                || (Boolean.TRUE.equals(account.isBanned())
                    && (account.bannedUntil() == null || account.bannedUntil().isAfter(LocalDateTime.now(clock))))) {
            return;
        }
        accounts.putIfAbsent(id, new Account(id, account.username(), account.email(), account.role(), current));
    }

    private CookieMutation cookie(String id, String value, int maxAge) {
        var config = jwtProperties.getCookie().getRefreshToken();
        return new CookieMutation(cookieName(id), value, maxAge, true,
                config.isSecure(), config.getSameSite(), config.getPath(), config.getDomain());
    }

    private static String cookieName(String id) {
        return SAVED_ACCOUNT_COOKIE_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }
}
