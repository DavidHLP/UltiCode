package com.ulticode.common.aspect;

import com.ulticode.common.annotation.CheckBan;
import com.ulticode.common.audit.BanCheckPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Ban-check aspect for {@link CheckBan}-annotated methods.
 *
 * <p><strong>Cross-cutting seam:</strong> the aspect depends only on
 * {@link BanCheckPort} — the user module ships the production adapter.
 * The aspect no longer imports {@code User} or {@code UserMapper}
 * directly. See {@code /tmp/architecture-review-1783485814.html}
 * candidate 4.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class BanCheckAspect {

    private final BanCheckPort banCheckPort;
    private final CurrentUserProvider currentUserProvider;

    @Before("@annotation(com.ulticode.common.annotation.CheckBan)")
    public void checkBan(JoinPoint joinPoint) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId != null && banCheckPort.isBanned(userId)) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }
    }
}
