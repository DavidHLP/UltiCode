package com.ulticode.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * XSS pass-through filter.
 *
 * <p>Previously attempted regex-based input sanitization, which corrupted
 * legitimate user content. XSS prevention is now handled via output encoding
 * with OWASP Encoder at the rendering layer (SEC-06).
 *
 * <p>This filter is retained as a pass-through to preserve its position in the
 * filter chain ordering. It will be removed in a future cleanup.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, response);
    }
}
