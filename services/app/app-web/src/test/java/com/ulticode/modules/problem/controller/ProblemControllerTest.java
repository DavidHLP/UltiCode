package com.ulticode.modules.problem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.security.AppTestSecurityConfig;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} for {@link ProblemController}.
 *
 * <p>Uses {@code addFilters=false} to bypass every security filter, isolating
 * the controller layer for request/response contract testing. The app-side
 * bounded {@code ProblemWebExceptionHandler} maps {@link
 * com.ulticode.app.error.ProblemErrorCode} and {@link
 * com.ulticode.common.error.BaseErrorCode} back to the shared {@code Result}
 * envelope. Admin-only endpoints rely on {@code @PreAuthorize}; their
 * authorization behavior is covered by integration tests.
 */
@WebMvcTest(controllers = ProblemController.class)
@Import(AppTestSecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProblemController")
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProblemService problemService;

    @MockBean
    private ProblemProjection problemProjection;

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
    }
}
