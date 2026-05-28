package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestServiceImpl")
class ContestServiceImplTest {

    @Mock
    private ContestMapper contestMapper;
    @Mock
    private ContestProblemMapper contestProblemMapper;
    @Mock
    private ContestParticipantMapper participantMapper;
    @Mock
    private GlobalRankingMapper globalRankingMapper;
    @Mock
    private ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock
    private ContestSchedulerService schedulerService;
    @Mock
    private RankingService rankingService;
    @Mock
    private AchievementTriggerService achievementTriggerService;
    @Mock
    private ContestSubmissionMapper contestSubmissionMapper;

    private ContestServiceImpl contestService;

    private static final String ADMIN_USER_ID = "123";
    private static final String REGULAR_USER_ID = "456";

    @BeforeEach
    void setUp() {
        contestService = new ContestServiceImpl(
                contestMapper,
                contestProblemMapper,
                participantMapper,
                globalRankingMapper,
                schedulerService,
                rankingService,
                achievementTriggerService,
                contestAnnouncementMapper,
                contestSubmissionMapper
        );
    }

    private void setAdminAuthentication() {
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                ADMIN_USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setRegularUserAuthentication() {
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                REGULAR_USER_ID,
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

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

    @Nested
    @DisplayName("createContest")
    class CreateContestTests {

        @Test
        @DisplayName("should create contest successfully when user is admin")
        void createContest_asAdmin_success() {
            setAdminAuthentication();

            CreateContestDTO dto = createValidDTO();
            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Weekly Contest #123");
            assertThat(result.getDescription()).isEqualTo("Test contest description");
            assertThat(result.getDuration()).isEqualTo(120);
            assertThat(result.getMaxParticipants()).isEqualTo(1000);
            assertThat(result.getIsPublished()).isTrue();
            assertThat(result.getStatus()).isEqualTo("DRAFT");
            assertThat(result.getCurrentParticipants()).isEqualTo(0);

            verify(contestMapper).insert(any(Contest.class));
            clearAuthentication();
        }

        @Test
        @DisplayName("should throw BusinessException when user is not admin")
        void createContest_asNonAdmin_forbidden() {
            setRegularUserAuthentication();

            CreateContestDTO dto = createValidDTO();

            assertThatThrownBy(() -> contestService.createContest(dto, REGULAR_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            verify(contestMapper, never()).insert(any(Contest.class));
            clearAuthentication();
        }

        @Test
        @DisplayName("should throw BusinessException when user is not authenticated")
        void createContest_unauthenticated_forbidden() {
            clearAuthentication();

            CreateContestDTO dto = createValidDTO();

            assertThatThrownBy(() -> contestService.createContest(dto, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            verify(contestMapper, never()).insert(any(Contest.class));
        }

        @Test
        @DisplayName("should set default values correctly")
        void createContest_defaultValues() {
            setAdminAuthentication();

            CreateContestDTO dto = createValidDTO();
            dto.setIsPublished(null);

            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result.getIsPublished()).isFalse();
            assertThat(result.getStatus()).isEqualTo("DRAFT");
            assertThat(result.getCurrentParticipants()).isEqualTo(0);

            verify(contestMapper).insert((Contest) argThat(contest ->
                    ((Contest) contest).getRegisteredCount() == 0 &&
                    ((Contest) contest).getParticipantCount() == 0 &&
                    ((Contest) contest).getSubmissionCount() == 0 &&
                    Boolean.FALSE.equals(((Contest) contest).getIsDeleted())
            ));
            clearAuthentication();
        }

        @Test
        @DisplayName("should generate slug from title")
        void createContest_generatesSlug() {
            setAdminAuthentication();

            CreateContestDTO dto = createValidDTO();
            dto.setTitle("Test Contest Title!");

            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result.getSlug()).isEqualTo("test-contest-title");
            clearAuthentication();
        }

        @Test
        @DisplayName("should calculate end time from start time and duration")
        void createContest_calculatesEndTime() {
            setAdminAuthentication();

            LocalDateTime startTime = LocalDateTime.of(2024, 12, 31, 10, 0);
            CreateContestDTO dto = createValidDTO();
            dto.setStartTime(startTime);
            dto.setDuration(120);

            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result.getEndTime()).isEqualTo(startTime.plusMinutes(120));
            clearAuthentication();
        }
    }
}
