package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.service.AccountSessionService;
import com.ulticode.auth.session.SessionCookieAdapter;
import com.ulticode.common.response.Result;
import com.ulticode.auth.dto.LoginDTO;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Browser account switching; no credential is returned in a response body. */
@RestController
@RequestMapping("/auth/accounts")
@RequiredArgsConstructor
public class AccountSessionsController {
    private final AccountSessionService accountSessionService;
    private final SessionCookieAdapter sessionCookieAdapter;

    public record SwitchAccountRequest(@NotBlank @Size(max = 128) String accountId, boolean adminOnly) {}
    public static class AddAccountRequest extends LoginDTO {
        private boolean adminOnly;
        public boolean isAdminOnly() { return adminOnly; }
        public void setAdminOnly(boolean value) { adminOnly = value; }
    }

    @GetMapping
    public Result<List<AccountSessionService.Account>> list(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Result.success(accountSessionService.list(cookies(request)));
    }

    @PostMapping
    @RateLimit(limit = 10, period = 60, key = "auth:accounts:add:ip:{ip}")
    public Result<LoginResponse> add(@Valid @RequestBody AddAccountRequest login,
            HttpServletRequest request, HttpServletResponse response) {
        var session = accountSessionService.add(login.getUsername(), login.getPassword(), cookies(request), login.isAdminOnly());
        sessionCookieAdapter.apply(session, response);
        return Result.success(session.response());
    }

    @PostMapping("/switch")
    @RateLimit(limit = 20, period = 60, key = "auth:accounts:switch:ip:{ip}")
    public Result<LoginResponse> switchAccount(@Valid @RequestBody SwitchAccountRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        var session = accountSessionService.switchTo(input.accountId(), cookies(request), input.adminOnly());
        sessionCookieAdapter.apply(session, response);
        return Result.success(session.response());
    }

    private static Map<String, String> cookies(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) values.putIfAbsent(cookie.getName(), cookie.getValue());
        }
        return values;
    }
}
