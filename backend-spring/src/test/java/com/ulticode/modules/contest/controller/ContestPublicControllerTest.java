package com.ulticode.modules.contest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {ContestSubmissionBridgeController.class, ContestCatalogController.class,
                ContestRankingController.class, ContestParticipationController.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ContestController public routes")
class ContestPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContestService contestService;

    @MockBean
    private ContestProjection contestProjection;

    @MockBean
    private RankingService rankingService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtProperties jwtProperties;

    private Contest contest() {
        Contest contest = new Contest();
        contest.setId("contest-running-001");
        contest.setSlug("ulticode-weekly-42");
        contest.setIsDeleted(false);
        return contest;
    }

    @Nested
    @DisplayName("contest problem submissions")
    class ContestProblemSubmissionsTests {

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("GET should route to the current user's contest problem submissions")
        void getContestProblemSubmissions_routes() throws Exception {
            Contest contest = contest();
            when(contestProjection.findById("ulticode-weekly-42")).thenReturn(Optional.empty());
            when(contestProjection.findBySlug("ulticode-weekly-42")).thenReturn(Optional.of(contest));
            when(contestProjection.getContestProblemSubmissions("contest-running-001", 1L, "user-1"))
                    .thenReturn(List.of());
            when(contestProjection.resolveContestProblemId("contest-running-001", "1")).thenReturn(1L);

            mockMvc.perform(get("/contest/ulticode-weekly-42/problems/1/submissions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("POST should accept language and code without problemId in the body")
        void submitContestProblem_routes() throws Exception {
            Contest contest = contest();
            CreateSubmissionDTO dto = new CreateSubmissionDTO();
            dto.setLanguage("java");
            dto.setCode("class Main {}");

            SubmissionVO submission = new SubmissionVO();
            submission.setId("submission-1");
            submission.setProblemId(1L);
            submission.setStatus("Pending");

            when(contestProjection.findById("ulticode-weekly-42")).thenReturn(Optional.empty());
            when(contestProjection.findBySlug("ulticode-weekly-42")).thenReturn(Optional.of(contest));
            when(contestProjection.resolveContestProblemId("contest-running-001", "1")).thenReturn(1L);
            when(contestService.submitContestProblem(eq("contest-running-001"), eq(1L), eq("user-1"), any(CreateSubmissionDTO.class)))
                    .thenReturn(submission);

            mockMvc.perform(post("/contest/ulticode-weekly-42/problems/1/submissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("submission-1"))
                    .andExpect(jsonPath("$.data.problemId").value(1))
                    .andExpect(jsonPath("$.data.status").value("Pending"));
        }
    }
}
