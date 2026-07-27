package com.ulticode.modules.contest.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-OWNER-001-B: contract tests for {@link DefaultContestOwnerPort}.
 *
 * <p>The port is the owner-only write surface for the contest tables.
 * These tests pin the contract that the legacy admin service
 * relies on, so a future regression (e.g., the port skipping the
 * status guard, or the problem-replace path leaking the
 * mid-batch failure) is caught at the seam.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultContestOwnerPort")
class DefaultContestOwnerPortTest {

    @Mock private ContestMapper contestMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestAnnouncementMapper contestAnnouncementMapper;

    private final Clock clock = Clock.systemUTC();
    /** Deterministic id generator so test assertions can pin ids. */
    private final UuidGenerator uuid = new FixedUuidGenerator("00000000-0000-0000-0000-000000000001");

    private ContestOwnerPort port;

    @BeforeEach
    void setUp() {
        port = new DefaultContestOwnerPort(
                contestMapper, contestProblemMapper, contestAnnouncementMapper,
                uuid, clock);
    }

    // ─── createContest ───────────────────────────────────────────

    @Nested
    @DisplayName("createContest()")
    class CreateContest {

        @Test
        @DisplayName("returns the generated id, inserts the row, optionally bulk-inserts scored problems")
        void insertsAndBulkInserts() {
            final CreateContestDTO dto = new CreateContestDTO();
            dto.setTitle("Weekly Contest #1");
            dto.setDescription("desc");
            dto.setStartTime(LocalDateTime.now(clock).plusDays(1));
            dto.setDuration(120);
            dto.setMaxParticipants(100);
            dto.setIsPublished(true);

            final String id = port.createContest(dto, "admin-1");

            assertThat(id).isEqualTo("00000000-0000-0000-0000-000000000001");
            verify(contestMapper).insert(any(Contest.class));
            // No problems in the DTO -> no bulk insert.
            verify(contestProblemMapper, never()).batchInsert(anyList());
        }

        @Test
        @DisplayName("surfaces a slug conflict as CONTEST_SLUG_EXISTS")
        void slugConflict() {
            final CreateContestDTO dto = new CreateContestDTO();
            dto.setTitle("Conflict");
            dto.setSlug("dup");
            dto.setStartTime(LocalDateTime.now(clock).plusDays(1));
            dto.setDuration(60);
            when(contestMapper.insert(any(Contest.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_contest_slug"));

            assertThatThrownBy(() -> port.createContest(dto, "admin-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_SLUG_EXISTS));
        }
    }

    // ─── updateContest ───────────────────────────────────────────

    @Nested
    @DisplayName("updateContest()")
    class UpdateContest {

        @Test
        @DisplayName("CONTEST_NOT_FOUND when the contest does not exist")
        void notFound() {
            when(contestMapper.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> port.updateContest("missing", new UpdateContestDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_NOT_FOUND));
        }

        @Test
        @DisplayName("CONTEST_ONLY_REGISTER_UPCOMING when the contest is past UPCOMING")
        void notUpcoming() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.RUNNING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);

            assertThatThrownBy(() -> port.updateContest("c1", new UpdateContestDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING));
        }

        @Test
        @DisplayName("happy path: applies the partial update and skips problem replace when neither is provided")
        void partialUpdateWithoutProblems() {
            final Contest before = new Contest();
            before.setId("c1");
            before.setTitle("Old");
            before.setStatus(ContestStatus.UPCOMING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);

            final UpdateContestDTO dto = new UpdateContestDTO();
            dto.setTitle("New");

            port.updateContest("c1", dto);

            verify(contestMapper).updateById(any(Contest.class));
            verify(contestProblemMapper, never()).deleteByContestId(anyString());
            verify(contestProblemMapper, never()).batchInsert(anyList());
        }
    }

    // ─── startContest / endContest ─────────────────────────────────

    @Nested
    @DisplayName("startContest()")
    class StartContest {

        @Test
        @DisplayName("CONTEST_NOT_STARTED when the contest is not UPCOMING")
        void notUpcoming() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.FINISHED.name());
            when(contestMapper.selectById("c1")).thenReturn(before);

            assertThatThrownBy(() -> port.startContest("c1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_NOT_STARTED));
        }

        @Test
        @DisplayName("CONTEST_NOT_FOUND when the contest has zero problems (legacy guard)")
        void noProblems() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.UPCOMING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);
            when(contestProblemMapper.findByContestId("c1")).thenReturn(List.of());

            assertThatThrownBy(() -> port.startContest("c1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_NOT_FOUND));
        }

        @Test
        @DisplayName("happy path: transitions to RUNNING")
        void happy() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.UPCOMING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);
            final ContestProblem cp = new ContestProblem();
            cp.setId("cp-1");
            when(contestProblemMapper.findByContestId("c1")).thenReturn(List.of(cp));

            port.startContest("c1");

            verify(contestMapper).updateById(any(Contest.class));
        }
    }

    @Nested
    @DisplayName("endContest()")
    class EndContest {

        @Test
        @DisplayName("CONTEST_ENDED when the contest is not RUNNING")
        void notRunning() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.UPCOMING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);

            assertThatThrownBy(() -> port.endContest("c1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_ENDED));
        }

        @Test
        @DisplayName("happy path: transitions to FINISHED")
        void happy() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.RUNNING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);

            port.endContest("c1");

            verify(contestMapper).updateById(any(Contest.class));
        }
    }

    // ─── deleteContest ───────────────────────────────────────────

    @Nested
    @DisplayName("deleteContest()")
    class DeleteContest {

        @Test
        @DisplayName("CONTEST_NOT_FOUND when the contest does not exist")
        void notFound() {
            when(contestMapper.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> port.deleteContest("missing", "admin-1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_NOT_FOUND));
        }

        @Test
        @DisplayName("happy path: soft-deletes the contest")
        void happy() {
            final Contest before = new Contest();
            before.setStatus(ContestStatus.UPCOMING.name());
            when(contestMapper.selectById("c1")).thenReturn(before);

            port.deleteContest("c1", "admin-1");

            verify(contestMapper).updateById(any(Contest.class));
        }
    }

    // ─── Announcements ───────────────────────────────────────────

    @Nested
    @DisplayName("createAnnouncement()")
    class CreateAnnouncement {

        @Test
        @DisplayName("returns the new id, inserts the row, no foreign-mapper FK to contests is exercised")
        void happy() {
            final Contest contest = new Contest();
            contest.setId("c1");
            when(contestMapper.selectById("c1")).thenReturn(contest);

            final String id = port.createAnnouncement("c1", "t", "c", true);

            assertThat(id).isEqualTo("00000000-0000-0000-0000-000000000001");
            verify(contestAnnouncementMapper).insert(any(ContestAnnouncement.class));
        }
    }

    @Nested
    @DisplayName("updateAnnouncement()")
    class UpdateAnnouncement {

        @Test
        @DisplayName("BAD_REQUEST when the announcement does not exist")
        void notFound() {
            when(contestAnnouncementMapper.findByContestIdAndId("c1", "missing"))
                    .thenReturn(null);

            assertThatThrownBy(() -> port.updateAnnouncement(
                    "c1", "missing", "t", "c", true))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.BAD_REQUEST));
        }

        @Test
        @DisplayName("happy path: applies the partial update")
        void happy() {
            final ContestAnnouncement before = new ContestAnnouncement();
            before.setId("a1");
            before.setTitle("old");
            when(contestAnnouncementMapper.findByContestIdAndId("c1", "a1"))
                    .thenReturn(before);

            port.updateAnnouncement("c1", "a1", "new", null, null);

            verify(contestAnnouncementMapper).updateById(any(ContestAnnouncement.class));
        }
    }

    @Nested
    @DisplayName("deleteAnnouncement()")
    class DeleteAnnouncement {

        @Test
        @DisplayName("BAD_REQUEST when the announcement does not exist")
        void notFound() {
            when(contestAnnouncementMapper.findByContestIdAndId("c1", "missing"))
                    .thenReturn(null);

            assertThatThrownBy(() -> port.deleteAnnouncement("c1", "missing"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.BAD_REQUEST));
        }

        @Test
        @DisplayName("happy path: deletes the row by id")
        void happy() {
            final ContestAnnouncement before = new ContestAnnouncement();
            before.setId("a1");
            when(contestAnnouncementMapper.findByContestIdAndId("c1", "a1"))
                    .thenReturn(before);

            port.deleteAnnouncement("c1", "a1");

            verify(contestAnnouncementMapper).deleteById("a1");
        }
    }
}
