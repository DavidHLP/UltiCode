package com.ulticode.modules.problem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for ProblemController.
 *
 * <p>Uses addFilters=false to bypass all security filters, isolating the
 * controller layer for request/response contract testing.</p>
 *
 * <p>Note: Admin-only endpoints (POST, PUT, DELETE) use @PreAuthorize which
 * requires method security. With addFilters=false and no security context,
 * these endpoints are accessible without authentication. The admin authorization
 * is tested separately in integration tests. Here we only verify the endpoint
 * exists and returns the expected response structure.</p>
 */
@WebMvcTest(
        value = ProblemController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProblemController")
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Service dependency (state machine + premium-guarded read entry points)
    @MockBean
    private ProblemService problemService;

    // Read-side projection dependency (list/detail/random/adjacent paths)
    @MockBean
    private ProblemProjection problemProjection;

    // SecurityConfig dependencies
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private JwtProperties jwtProperties;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean
    private CorsProperties corsProperties;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Nested
    @DisplayName("GET /problems")
    class ListProblemsTests {

        @Test
        @DisplayName("should return 200 with paginated result")
        void listProblems_success() throws Exception {
            PageResult<ProblemVO> pageResult = PageResult.of(List.of(), 0L, 1, 20);
            when(problemProjection.listProblems(any())).thenReturn(pageResult);

            mockMvc.perform(get("/problems"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.data.total").value(0))
                    .andExpect(jsonPath("$.data.items").isArray());
        }

        @Test
        @DisplayName("should return 200 with filters applied")
        void listProblems_withFilters() throws Exception {
            ProblemVO problemVO = new ProblemVO();
            problemVO.setId(1L);
            problemVO.setTitle("Two Sum");
            problemVO.setDifficulty("EASY");

            PageResult<ProblemVO> pageResult = PageResult.of(List.of(problemVO), 1L, 1, 20);
            when(problemProjection.listProblems(any())).thenReturn(pageResult);

            mockMvc.perform(get("/problems")
                            .param("page", "1")
                            .param("pageSize", "10")
                            .param("difficulty", "EASY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items[0].id").value(1))
                    .andExpect(jsonPath("$.data.items[0].title").value("Two Sum"));
        }
    }

    @Nested
    @DisplayName("GET /problems/{id}")
    class GetProblemByIdTests {

        @Test
        @DisplayName("should return 200 with problem detail")
        void getProblemById_success() throws Exception {
            ProblemDetailPublicVO response = new ProblemDetailPublicVO();
            response.setId(1L);
            response.setTitle("Two Sum");
            response.setSlug("two-sum");
            response.setDifficulty("EASY");

            when(problemProjection.publicDetailById(1L)).thenReturn(response);

            mockMvc.perform(get("/problems/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Two Sum"))
                    .andExpect(jsonPath("$.data.slug").value("two-sum"));
        }
    }

    @Nested
    @DisplayName("GET /problems/slug/{slug}")
    class GetProblemBySlugTests {

        @Test
        @DisplayName("should return 200 with problem detail")
        void getProblemBySlug_success() throws Exception {
            ProblemDetailPublicVO response = new ProblemDetailPublicVO();
            response.setId(1L);
            response.setTitle("Two Sum");
            response.setSlug("two-sum");

            when(problemProjection.publicDetailBySlug("two-sum")).thenReturn(response);

            mockMvc.perform(get("/problems/slug/two-sum"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Two Sum"));
        }
    }

    @Nested
    @DisplayName("Admin-only endpoints")
    class AdminEndpointTests {

        @Test
        @DisplayName("POST /problems should return 200 (admin auth tested in integration)")
        void createProblem_success() throws Exception {
            ProblemVO problemVO = new ProblemVO();
            problemVO.setId(1L);
            problemVO.setTitle("Test Problem");

            when(problemService.createProblem(any())).thenReturn(problemVO);

            String json = "{\"title\":\"Test Problem\",\"slug\":\"test-problem\",\"difficulty\":\"Easy\"}";

            mockMvc.perform(post("/problems")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Test Problem"));
        }

        @Test
        @DisplayName("DELETE /problems/{id} should return 200 (admin auth tested in integration)")
        void deleteProblem_success() throws Exception {
            mockMvc.perform(delete("/problems/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}
