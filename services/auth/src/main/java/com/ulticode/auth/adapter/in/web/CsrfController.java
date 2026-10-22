package com.ulticode.auth.adapter.in.web;

import static com.ulticode.websecurity.csrf.CookieCsrfFilter.CSRF_TOKEN_COOKIE;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.session.SessionCookieAdapter;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Issues a double-submit token even when only saved-account credentials remain. */
@RestController
@RequestMapping("/auth/csrf")
@RequiredArgsConstructor
public class CsrfController {
    private final AuthSessionPort sessions;
    private final SessionCookieAdapter cookies;
    public record CsrfResponse(String csrfToken) {}

    @GetMapping
    @RateLimit(limit = 60, period = 60, key = "auth:csrf:ip:{ip}")
    public Result<CsrfResponse> csrf(@CookieValue(name = CSRF_TOKEN_COOKIE, required = false) String existing,
            HttpServletResponse response) {
        var session = sessions.csrfSession(existing);
        cookies.apply(session, response);
        response.setHeader("Cache-Control", "no-store");
        return Result.success(new CsrfResponse(session.response().getCsrfToken()));
    }
}
