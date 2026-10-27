package com.ulticode.app.security;

import com.ulticode.websecurity.jwt.AccessTokenVerifier;
import com.ulticode.websecurity.jwt.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AppErrorDispatchSecurityTest.Probe.class)
@Import({AppSecurityConfig.class, AppErrorDispatchSecurityTest.Filters.class, AppErrorDispatchSecurityTest.Probe.class})
@TestPropertySource(properties = "jwt.resource-server.enabled=false")
class AppErrorDispatchSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AccessTokenVerifier verifier;
    @MockitoBean private org.springframework.security.core.userdetails.UserDetailsService users;

    @Test
    void serverErrorDispatchPreservesFailureInsteadOfRequestingRefresh() throws Exception {
        // The servlet container starts an ERROR dispatch after the initial
        // request/filter context has unwound; OncePerRequestFilter skips it.
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; }))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void ordinaryRequestsRemainProtectedIncludingErrorPath() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(get("/submissions/probe")).andExpect(status().isUnauthorized());
        mvc.perform(get("/submissions/probe").with(user("actor").roles("USER")))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class Filters {
        @Bean JwtAuthenticationFilter jwtFilter(AccessTokenVerifier verifier) {
            return new JwtAuthenticationFilter(verifier);
        }
    }

    @RestController
    static class Probe {
        @GetMapping("/error") ResponseEntity<Void> error() {
            return ResponseEntity.internalServerError().build();
        }
        @GetMapping("/submissions/probe") String privateEndpoint() { return "ok"; }
    }
}
