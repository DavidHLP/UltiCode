package com.ulticode.modules.contest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.app.security.AppTestSecurityConfig;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestParticipationService;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.contest.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} for contest public controllers.
 *
 * <p>Uses {@code addFilters=false} to bypass every security filter, isolating
 * the controller layer for request/response contract testing. Rewritten to
 * match {@code ProblemControllerTest} pattern during P7-RELOCATE-CONTEST-001:
 * removed legacy infrastructure deps (UlticodeBackendApplication, MapperConfig,
 * JwtTokenProvider, JwtProperties).
 */
@WebMvcTest(controllers = {
        ContestSubmissionBridgeController.class,
        ContestCatalogController.class,
        ContestRankingController.class,
        ContestParticipationController.class
})
@Import(AppTestSecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Contest public routes")
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
    private ContestParticipationService participationService;

    @MockBean
    private com.ulticode.modules.contest.service.impl.RankingServiceImpl rankingServiceImpl;

    @MockBean
    private CurrentUserProvider currentUserProvider;


    @BeforeEach
    void stubCurrentUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("user-1");
        when(currentUserProvider.isAuthenticated()).thenReturn(true);
    }

    private Contest contest() {
        Contest contest = new Contest();
        contest.setId("contest-running-001");
        contest.setSlug("ulticode-weekly-42");
        contest.setIsDeleted(false);
        return contest;
    }

    @Test
    @DisplayName("GET contest detail passes the raw identifier to the public projection")
    void getContestById_usesRawIdentifier() throws Exception {
        when(contestProjection.getPublicContestById("draft", "user-1")).thenReturn(null);

        mockMvc.perform(get("/contest/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(contestProjection).getPublicContestById("draft", "user-1");
    }

    @Nested
    @DisplayName("contest problem submissions")
    class ContestProblemSubmissionsTests {

        @Test
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
