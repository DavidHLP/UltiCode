package com.ulticode.auth.security;

import com.ulticode.auth.security.jwt.AuthAccessTokenVerifier;
import com.ulticode.auth.security.jwt.AuthJwtFilterConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthSecurityConfigContractTest.SecurityProbeController.class)
@Import({AuthSecurityConfig.class, AuthAuthenticationEntryPoint.class, AuthJwtFilterConfiguration.class})
class AuthSecurityConfigContractTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AuthAccessTokenVerifier accessTokenVerifier;


    @Test
    void currentSessionEndpointsRequireAuthenticationAtFilterChain() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/auth/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class SecurityProbeController {

        @GetMapping({"/auth/me", "/auth/permissions"})
        String probe() {
            return "reached-controller";
        }
    }
}
