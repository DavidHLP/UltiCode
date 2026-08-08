package com.ulticode.auth.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.List;

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
        Cookie cookie = new Cookie(mutation.name(), mutation.value());
        cookie.setHttpOnly(mutation.httpOnly());
        cookie.setSecure(mutation.secure());
        cookie.setPath(mutation.path());
        cookie.setMaxAge(mutation.maxAgeSeconds());
        response.addCookie(cookie);
    }
}
