package com.ulticode.app.security;

import com.ulticode.app.error.SolutionErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Ban-check aspect for {@link CheckBan}-annotated methods.
 *
 * <p>App-side replacement for legacy
 * {@code com.ulticode.common.aspect.BanCheckAspect}. Depends only on
 * {@link BanCheckPort} (app-side adapter via IdentityQueryService) and
 * {@link CurrentUserProvider} (from backend-web-security).
 *
 * <p>P7-RELOCATE-SOLUTION-001: required when backend-app stopped depending
 * on backend-legacy.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BanCheckAspect {

    private final BanCheckPort banCheckPort;
    private final CurrentUserProvider currentUserProvider;

    @Before("@annotation(com.ulticode.app.security.CheckBan)")
    public void checkBan(JoinPoint joinPoint) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId != null && banCheckPort.isBanned(userId)) {
            throw new BusinessException(SolutionErrorCode.USER_BANNED);
        }
    }
}
