package com.ulticode.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Routes only enabled Auth/Admin HTTP surfaces through their own MVC and security graphs. */
@Configuration(proxyBeanMethods = false)
class CoreOwnerHttpConfiguration {
    @Bean
    FilterRegistrationBean<OwnerHttpFilter> coreOwnerHttpFilter(CoreOwnerContextManager owners) {
        var registration = new FilterRegistrationBean<>(new OwnerHttpFilter(owners));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    static final class OwnerHttpFilter extends OncePerRequestFilter {
        private final CoreOwnerContextManager owners;
        private final Map<String, DispatcherServlet> dispatchers = new ConcurrentHashMap<>();

        OwnerHttpFilter(CoreOwnerContextManager owners) {
            this.owners = owners;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain parent) throws ServletException, IOException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            String owner = path.equals("/auth") || path.startsWith("/auth/") ? "auth"
                    : path.equals("/admin") || path.startsWith("/admin/") ? "admin" : null;
            if (owner == null) {
                parent.doFilter(request, response);
                return;
            }
            if (owners.states().get(owner) != CoreOwnerContextManager.State.READY) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":503,\"message\":\"Core Owner is not ready\","
                        + "\"data\":null,\"traceId\":\"t-core\"}");
                return;
            }
            DispatcherServlet dispatcher;
            synchronized (dispatchers) {
                dispatcher = dispatchers.get(owner);
                if (dispatcher == null) {
                    dispatcher = owners.bean(owner, DispatcherServlet.class);
                    ServletContext servletContext = request.getServletContext();
                    String servletName = "core-" + owner;
                    dispatcher.init(new ServletConfig() {
                        public String getServletName() { return servletName; }
                        public ServletContext getServletContext() { return servletContext; }
                        public String getInitParameter(String name) { return null; }
                        public Enumeration<String> getInitParameterNames() {
                            return Collections.emptyEnumeration();
                        }
                    });
                    dispatchers.put(owner, dispatcher);
                }
            }
            DispatcherServlet target = dispatcher;
            owners.bean(owner, FilterChainProxy.class).doFilter(request, response,
                    (securedRequest, securedResponse) -> target.service(securedRequest, securedResponse));
        }

        @Override
        public void destroy() {
            synchronized (dispatchers) {
                dispatchers.values().forEach(DispatcherServlet::destroy);
                dispatchers.clear();
            }
        }
    }
}
