package com.ulticode.modules.contest.service.impl;
import com.ulticode.common.error.BaseErrorCode;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingServiceImpl")
class RankingServiceImplTest {

    @Mock
    private ContestParticipantMapper participantMapper;
    @Mock
    private ContestMapper contestMapper;
    @Mock
    private com.ulticode.modules.contest.scoring.ScoringStrategyResolver scoringStrategyResolver;
    @Mock
    private com.ulticode.modules.contest.scoring.ScoringStrategy scoringStrategy;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new RankingServiceImpl(participantMapper, contestMapper, scoringStrategyResolver);
        org.mockito.Mockito.lenient().when(scoringStrategyResolver.resolveFromString(org.mockito.ArgumentMatchers.any()))
                .thenReturn(scoringStrategy);
    }

    private ContestParticipantMapper.ContestParticipantWithUser createParticipant(String userId, int rank, int score) {
        return new ContestParticipantMapper.ContestParticipantWithUser(
                "p-" + userId,
                "c1",
                userId,
                "FINISHED",
                rank,
                score,
                0,
                0,
                1,
                java.time.LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                java.time.LocalDateTime.of(2024, 1, 1, 0, 0, 0),
                null,
                "user-" + userId,
                "User " + userId,
                "https://avatar.example.com/" + userId + ".png",
                30,
                2
        );
    }

    @Test
    @DisplayName("public ranking hides soft-deleted contests")
    void getPublicContestRanking_deletedContest_throwsNotFound() {
        Contest deleted = new Contest();
        deleted.setId("contest-123");
        deleted.setIsVisible(true);
        deleted.setIsDeleted(true);
        when(contestMapper.selectById("contest-123")).thenReturn(deleted);

        assertThatThrownBy(() -> ((RankingServiceImpl) rankingService)
                .getPublicContestRanking("contest-123", 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_FOUND);

        verify(participantMapper, never()).countRankedParticipantsByContestId("contest-123");
    }

    @Nested
    @DisplayName("getContestRanking")
    class GetContestRankingTests {

        @Test
        @DisplayName("should return paginated results with user data")
        void getContestRanking_success() {
            String contestId = "contest-123";
            ContestParticipantMapper.ContestParticipantWithUser p1 = createParticipant("u1", 1, 500);
            ContestParticipantMapper.ContestParticipantWithUser p2 = createParticipant("u2", 2, 400);

            when(participantMapper.countRankedParticipantsByContestId(contestId)).thenReturn(10L);
            when(participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, 20, 0))
                    .thenReturn(List.of(p1, p2));

            PageResult<ContestRankingVO> result = rankingService.getContestRanking(contestId, 1, 20);

            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(10);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(20);
            assertThat(result.getItems()).hasSize(2);

            ContestRankingVO first = result.getItems().get(0);
            assertThat(first.getRank()).isEqualTo(1);
            assertThat(first.getUserId()).isEqualTo("u1");
            assertThat(first.getScore()).isEqualTo(500L);
            assertThat(first.getUsername()).isEqualTo("user-u1");
            assertThat(first.getName()).isEqualTo("User u1");
            assertThat(first.getAvatar()).isEqualTo("https://avatar.example.com/u1.png");
            assertThat(first.getProblemsSolved()).isEqualTo(2);

            ContestRankingVO second = result.getItems().get(1);
            assertThat(second.getRank()).isEqualTo(2);
            assertThat(second.getUserId()).isEqualTo("u2");
            assertThat(second.getScore()).isEqualTo(400L);

            verify(participantMapper).countRankedParticipantsByContestId(contestId);
            verify(participantMapper).selectParticipantsWithUserByContestIdPaginated(contestId, 20, 0);
        }

        @Test
        @DisplayName("should use correct offset for page 2")
        void getContestRanking_page2_correctOffset() {
            String contestId = "contest-123";
            ContestParticipantMapper.ContestParticipantWithUser p3 = createParticipant("u3", 3, 300);

            when(participantMapper.countRankedParticipantsByContestId(contestId)).thenReturn(10L);
            when(participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, 20, 20))
                    .thenReturn(List.of(p3));

            PageResult<ContestRankingVO> result = rankingService.getContestRanking(contestId, 2, 20);

            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getRank()).isEqualTo(3);

            verify(participantMapper).selectParticipantsWithUserByContestIdPaginated(contestId, 20, 20);
        }

        @Test
        @DisplayName("should default to page 1 and limit 50 when params are null")
        void getContestRanking_nullParams_usesDefaults() {
            String contestId = "contest-123";

            when(participantMapper.countRankedParticipantsByContestId(contestId)).thenReturn(0L);
            when(participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, 50, 0))
                    .thenReturn(List.of());

            PageResult<ContestRankingVO> result = rankingService.getContestRanking(contestId, null, null);

            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(50);
            assertThat(result.getItems()).isEmpty();

            verify(participantMapper).selectParticipantsWithUserByContestIdPaginated(contestId, 50, 0);
        }

        @Test
        @DisplayName("should clamp limit to max 100")
        void getContestRanking_clampsLimit() {
            String contestId = "contest-123";

            when(participantMapper.countRankedParticipantsByContestId(contestId)).thenReturn(0L);
            when(participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, 100, 0))
                    .thenReturn(List.of());

            PageResult<ContestRankingVO> result = rankingService.getContestRanking(contestId, 1, 200);

            assertThat(result.getPageSize()).isEqualTo(100);
            verify(participantMapper).selectParticipantsWithUserByContestIdPaginated(contestId, 100, 0);
        }

        @Test
        @DisplayName("should throw BusinessException when contestId is null")
        void getContestRanking_nullContestId_throwsException() {
            assertThatThrownBy(() -> rankingService.getContestRanking(null, 1, 20))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", BaseErrorCode.BAD_REQUEST);
        }

        @Test
        @DisplayName("should throw BusinessException when contestId is blank")
        void getContestRanking_blankContestId_throwsException() {
            assertThatThrownBy(() -> rankingService.getContestRanking("   ", 1, 20))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", BaseErrorCode.BAD_REQUEST);
        }

        @Test
        @DisplayName("should default page to 1 when zero or negative")
        void getContestRanking_zeroPage_defaultsToOne() {
            String contestId = "contest-123";

            when(participantMapper.countRankedParticipantsByContestId(contestId)).thenReturn(0L);
            when(participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, 20, 0))
                    .thenReturn(List.of());

            PageResult<ContestRankingVO> result = rankingService.getContestRanking(contestId, 0, 20);

            assertThat(result.getPage()).isEqualTo(1);
            verify(participantMapper).selectParticipantsWithUserByContestIdPaginated(contestId, 20, 0);
        }
    }
}
