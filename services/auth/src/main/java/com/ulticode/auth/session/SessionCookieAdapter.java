package com.ulticode.auth.session;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Applies workflow-owned cookie mutations at the HTTP boundary.
 */
@Component
public class SessionCookieAdapter {

    public void apply(AuthSession session, HttpServletResponse response) {
        if (session == null) {
            return;
        }
        applyCookies(session.cookies(), response);
    }

    public void applyCookies(List<CookieMutation> mutations, HttpServletResponse response) {
        if (mutations == null || response == null) {
            return;
        }
        mutations.forEach(mutation -> applyCookie(mutation, response));
    }

    private void applyCookie(CookieMutation mutation, HttpServletResponse response) {
        if (mutation == null) {
            return;
        }
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(mutation.name(), mutation.value())
                .httpOnly(mutation.httpOnly())
                .secure(mutation.secure())
                .sameSite(mutation.sameSite())
                .path(mutation.path())
                .maxAge(mutation.maxAgeSeconds());
        if (mutation.domain() != null) {
            builder.domain(mutation.domain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
