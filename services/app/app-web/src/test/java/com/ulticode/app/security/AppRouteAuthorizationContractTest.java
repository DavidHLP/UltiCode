package com.ulticode.app.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ulticode.app.security.jwt.JwtAuthenticationFilter;
import com.ulticode.app.security.jwt.ResourceServerJwtVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = AppRouteAuthorizationContractTest.ProbeController.class)
@ContextConfiguration(classes = {AppRouteAuthorizationContractTest.ProbeController.class,
        AppSecurityConfig.class, JwtAuthenticationFilter.class})
class AppRouteAuthorizationContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResourceServerJwtVerifier jwtVerifier;

    @Test
    void anonymousCanReadExplicitPublicCatalog() throws Exception {
        mockMvc.perform(get("/problems/1"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCanUseExplicitPublicCodeRun() throws Exception {
        mockMvc.perform(post("/problems/1/submissions/run"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotReachUnmatchedAppRoute() throws Exception {
        mockMvc.perform(get("/private-probe"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ordinaryUserCannotReachAppAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/probe").with(user("user-1").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReachAppAdminRoute() throws Exception {
        mockMvc.perform(get("/admin/probe").with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/problems/1", "/private-probe", "/admin/probe"})
        String read() {
            return "ok";
        }

        @PostMapping("/problems/1/submissions/run")
        String run() {
            return "ok";
        }
    }
}
