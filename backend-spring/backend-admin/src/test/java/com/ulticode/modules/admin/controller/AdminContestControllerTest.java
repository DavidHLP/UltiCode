package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.UlticodeBackendApplication;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.service.AdminContestMutationService;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.security.jwt.JwtTokenProvider;
import com.ulticode.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * @WebMvcTest for ContestController.
 *
 * <p>Tests contest creation endpoint with security context.</p>
 */
@WebMvcTest(
        value = AdminContestController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@ContextConfiguration(classes = UlticodeBackendApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminContestController")
class ContestControllerTest {
    @org.junit.jupiter.api.BeforeEach
    void stubCurrentUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("test-user-id");
        when(currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")).thenReturn(true);
        when(currentUserProvider.hasRole("ADMIN")).thenReturn(true);
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);
    }


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContestService contestService;

    @MockBean
    private AdminContestMutationService adminContestMutation;

    @MockBean
    private com.ulticode.modules.admin.service.ContestCutoverService contestCutoverService;

    @MockBean
    private ContestProjection contestProjection;

    @MockBean
    private RankingService rankingService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtProperties jwtProperties;
    @MockBean
    private CurrentUserProvider currentUserProvider;

    private CreateContestDTO createValidDTO() {
        CreateContestDTO dto = new CreateContestDTO();
        dto.setTitle("Weekly Contest #123");
        dto.setDescription("Test contest description");
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setDuration(120);
        dto.setMaxParticipants(1000);
        dto.setIsPublished(true);
        return dto;
    }

    private AdminContestVO createValidAdminContestVO() {
        AdminContestVO vo = new AdminContestVO();
        vo.setId("contest-uuid-123");
        vo.setSlug("weekly-contest-123");
        vo.setTitle("Weekly Contest #123");
        vo.setDescription("Test contest description");
        vo.setStatus("UPCOMING");
        vo.setDurationMinutes(120);
        vo.setIsVisible(true);
        vo.setParticipantCount(0);
        return vo;
    }

    @Nested
    @DisplayName("POST /admin/contest")
    class CreateContestTests {

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("should return 200 with created contest when admin creates contest")
        void createContest_success_asAdmin() throws Exception {
            AdminContestVO adminVO = createValidAdminContestVO();
            when(contestCutoverService.createContest(any(CreateContestDTO.class), anyString()))
                    .thenReturn(adminVO);

            CreateContestDTO dto = createValidDTO();

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("contest-uuid-123"))
                    .andExpect(jsonPath("$.data.title").value("Weekly Contest #123"))
                    .andExpect(jsonPath("$.data.slug").value("weekly-contest-123"))
                    .andExpect(jsonPath("$.data.status").value("UPCOMING"))
                    .andExpect(jsonPath("$.data.durationMinutes").value(120))
                    .andExpect(jsonPath("$.data.isVisible").value(true));
        }

        @Test
        @WithMockUser(roles = {"SUPER_ADMIN"})
        @DisplayName("should return 200 with created contest when super admin creates contest")
        void createContest_success_asSuperAdmin() throws Exception {
            AdminContestVO adminVO = createValidAdminContestVO();
            when(contestCutoverService.createContest(any(CreateContestDTO.class), anyString()))
                    .thenReturn(adminVO);

            CreateContestDTO dto = createValidDTO();

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.title").value("Weekly Contest #123"));
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("should return 403 when non-admin user tries to create contest")
        void createContest_forbidden_asUser() throws Exception {
            when(contestCutoverService.createContest(any(CreateContestDTO.class), anyString()))
                    .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

            CreateContestDTO dto = createValidDTO();

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("should return 400 when title is blank")
        void createContest_validationError_blankTitle() throws Exception {
            CreateContestDTO dto = createValidDTO();
            dto.setTitle("");

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("should return 400 when start time is in the past")
        void createContest_validationError_pastStartTime() throws Exception {
            CreateContestDTO dto = createValidDTO();
            dto.setStartTime(LocalDateTime.now().minusDays(1));

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("should return 400 when duration is less than 5 minutes")
        void createContest_validationError_durationTooShort() throws Exception {
            CreateContestDTO dto = createValidDTO();
            dto.setDuration(3);

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("should return 400 when duration exceeds 1440 minutes")
        void createContest_validationError_durationTooLong() throws Exception {
            CreateContestDTO dto = createValidDTO();
            dto.setDuration(2000);

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 401 when not authenticated")
        void createContest_unauthenticated() throws Exception {
            when(currentUserProvider.getCurrentUserId()).thenReturn(null);
            CreateContestDTO dto = createValidDTO();

            mockMvc.perform(post("/admin/contest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
