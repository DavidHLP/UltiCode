package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.port.AdminContestReadPort;
import com.ulticode.modules.admin.port.ContestAnnouncementPushPort;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.websocket.contest.dto.AnnouncementPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminContestMutationServiceImpl} &mdash; the
 * contest write state machine lifted out of the legacy
 * {@code AdminContestServiceImpl} per the admin-write deepening.
 *
 * <p>Covers every mutation invariant the module owns, with a pure
 * mock-mapper graph and no database: status-transition guards, write
 * invariants (existence, slug conflict, duplicate problem), the
 * fire-and-forget announcement push (D-12), the audit-context payloads,
 * the problem-count read seam, and the partial-update shape.
 *
 * <p>Behavior is preserved exactly from the legacy single service; these
 * cases pin that contract against the new write seam.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminContestMutationServiceImpl (admin contest writes)")
class AdminContestMutationServiceImplTest {

    private static final String ADMIN_USER_ID = "admin-1";
    private static final String CONTEST_ID = "contest-1";

    @Mock private ContestMapper contestMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock private ContestAnnouncementPushPort contestAnnouncementPushPort;
    @Mock private AdminContestReadPort contestReadPort;
    @Mock private AuditHelper auditHelper;
    private final Clock clock = Clock.systemUTC();
    @Mock private AdminContestProjection adminContestProjection;
    @Mock private CurrentUserProvider currentUserProvider;

    private AdminContestMutationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminContestMutationServiceImpl(
                contestMapper, contestProblemMapper, contestAnnouncementMapper,
                contestAnnouncementPushPort, contestReadPort, auditHelper, clock,
                adminContestProjection, currentUserProvider);
    }

    @AfterEach
    void tearDown() {
        // createContest/updateContest/etc. populate AuditContext ThreadLocals that
        // the @Audited aspect would clear in production but unit tests never invoke.
        // Without this, userId leaks into later test classes on the same Surefire thread.
        AuditContext.clear();
    }

    // ----------------------------------------------------------------------
    // createContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("createContest — create + optional problem bulk-insert")
    class CreateContest {

        @Test
        @DisplayName("inserts the contest, generates a slug, and returns the projected VO")
        void happyPath_insertsAndProjects() {
            CreateContestDTO dto = buildCreateDto("Weekly #21", LocalDateTime.of(2026, 7, 1, 10, 0),
                    180, false, null);
            when(adminContestProjection.generateSlug("Weekly #21")).thenReturn("weekly-21");
            AdminContestVO vo = new AdminContestVO();
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(vo);

            AdminContestVO result = service.createContest(dto, ADMIN_USER_ID);

            assertThat(result).isSameAs(vo);

            ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
            verify(contestMapper).insert(captor.capture());
            Contest saved = captor.getValue();
            assertThat(saved.getSlug()).isEqualTo("weekly-21");
            assertThat(saved.getStatus()).isEqualTo(ContestStatus.UPCOMING.name());
            assertThat(saved.getEndTime()).isEqualTo(dto.getStartTime().plusMinutes(180));
            assertThat(saved.getIsVisible()).isFalse();
            assertThat(saved.getIsDeleted()).isFalse();
            verify(contestProblemMapper, never()).batchInsert(any());
        }

        @Test
        @DisplayName("isPublished=true flips isVisible on the created contest")
        void publishedFlag_setsVisible() {
            CreateContestDTO dto = buildCreateDto("Open Cup", LocalDateTime.of(2026, 7, 1, 10, 0),
                    120, true, null);
            when(adminContestProjection.generateSlug(anyString())).thenReturn("open-cup");
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());

            service.createContest(dto, ADMIN_USER_ID);

            ArgumentCaptor<Contest> captor = ArgumentCaptor.forClass(Contest.class);
            verify(contestMapper).insert(captor.capture());
            assertThat(captor.getValue().getIsVisible()).isTrue();
        }

        @Test
        @DisplayName("bulk-inserts contest problems when problemIds is provided")
        void withProblemIds_bulkInserts() {
            CreateContestDTO dto = buildCreateDto("With Problems", LocalDateTime.of(2026, 7, 1, 10, 0),
                    120, false, List.of(101L, 102L, 103L));
            when(adminContestProjection.generateSlug(anyString())).thenReturn("with-problems");
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());

            service.createContest(dto, ADMIN_USER_ID);

            ArgumentCaptor<List<ContestProblem>> captor = ArgumentCaptor.forClass(List.class);
            verify(contestProblemMapper).batchInsert(captor.capture());
            List<ContestProblem> inserted = captor.getValue();
            assertThat(inserted).hasSize(3);
            assertThat(inserted.get(0).getProblemIndex()).isEqualTo("Q1");
            assertThat(inserted.get(0).getProblemId()).isEqualTo(101L);
            assertThat(inserted.get(0).getBaseScore()).isEqualTo(100);
            assertThat(inserted.get(1).getProblemIndex()).isEqualTo("Q2");
            assertThat(inserted.get(2).getProblemIndex()).isEqualTo("Q3");
        }

        @Test
        @DisplayName("does NOT call batchInsert when problemIds is empty (null-or-empty guard)")
        void emptyProblemIds_skipsBatchInsert() {
            CreateContestDTO dto = buildCreateDto("No Problems", LocalDateTime.of(2026, 7, 1, 10, 0),
                    120, false, List.of());
            when(adminContestProjection.generateSlug(anyString())).thenReturn("no-problems");
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());

            service.createContest(dto, ADMIN_USER_ID);

            verify(contestProblemMapper, never()).batchInsert(any());
        }

        @Test
        @DisplayName("P0-5 / H2: slug unique-constraint violation maps to CONTEST_SLUG_EXISTS")
        void slugConflict_mapsToSlugExists() {
            CreateContestDTO dto = buildCreateDto("Dup", LocalDateTime.of(2026, 7, 1, 10, 0),
                    120, false, null);
            when(adminContestProjection.generateSlug(anyString())).thenReturn("dup");
            org.springframework.dao.DataIntegrityViolationException violation =
                    new DataIntegrityViolationException("uk_contest_slug");
            org.mockito.Mockito.doThrow(violation).when(contestMapper).insert(any(Contest.class));

            assertThatThrownBy(() -> service.createContest(dto, ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_SLUG_EXISTS);
        }
    }

    // ----------------------------------------------------------------------
    // updateContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("updateContest — UPCOMING-only partial update")
    class UpdateContest {

        @Test
        @DisplayName("throws CONTEST_NOT_FOUND when the contest does not exist")
        void missingContest_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(null);
            UpdateContestDTO dto = new UpdateContestDTO();

            assertThatThrownBy(() -> service.updateContest(CONTEST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
        }

        @Test
        @DisplayName("throws CONTEST_ONLY_REGISTER_UPCOMING when the contest is RUNNING")
        void runningContest_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.RUNNING));
            UpdateContestDTO dto = new UpdateContestDTO();

            assertThatThrownBy(() -> service.updateContest(CONTEST_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }

        @Test
        @DisplayName("applies partial fields and persists the update")
        void happyPath_appliesPartialFields() {
            Contest contest = buildContest(ContestStatus.UPCOMING);
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());
            UpdateContestDTO dto = new UpdateContestDTO();
            dto.setTitle("Renamed");
            dto.setDescription("new desc");

            service.updateContest(CONTEST_ID, dto);

            verify(contestMapper).updateById(contest);
            assertThat(contest.getTitle()).isEqualTo("Renamed");
            assertThat(contest.getDescription()).isEqualTo("new desc");
        }

        @Test
        @DisplayName("duration change recomputes the coupled endTime from the new startTime")
        void durationChange_recomputesEndTime() {
            Contest contest = buildContest(ContestStatus.UPCOMING);
            LocalDateTime newStart = LocalDateTime.of(2026, 8, 1, 9, 0);
            contest.setStartTime(LocalDateTime.of(2026, 7, 1, 10, 0));
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());
            UpdateContestDTO dto = new UpdateContestDTO();
            dto.setStartTime(newStart);
            dto.setDuration(90);

            service.updateContest(CONTEST_ID, dto);

            assertThat(contest.getEndTime()).isEqualTo(newStart.plusMinutes(90));
            assertThat(contest.getDurationMinutes()).isEqualTo(90);
        }

        @Test
        @DisplayName("duration change without startTime recomputes endTime from the existing startTime")
        void durationChangeWithoutStartTime_usesExistingStartTime() {
            Contest contest = buildContest(ContestStatus.UPCOMING);
            LocalDateTime existingStart = LocalDateTime.of(2026, 7, 1, 10, 0);
            contest.setStartTime(existingStart);
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());
            UpdateContestDTO dto = new UpdateContestDTO();
            dto.setDuration(60);

            service.updateContest(CONTEST_ID, dto);

            assertThat(contest.getEndTime()).isEqualTo(existingStart.plusMinutes(60));
        }

        @Test
        @DisplayName("problemIds replace deletes the old set then bulk-inserts the new set")
        void problemIdsReplace_deletesThenInserts() {
            Contest contest = buildContest(ContestStatus.UPCOMING);
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            when(adminContestProjection.toAdminVO(any(Contest.class))).thenReturn(new AdminContestVO());
            UpdateContestDTO dto = new UpdateContestDTO();
            dto.setProblemIds(List.of(201L, 202L));

            service.updateContest(CONTEST_ID, dto);

            verify(contestProblemMapper).deleteByContestId(CONTEST_ID);
            ArgumentCaptor<List<ContestProblem>> captor = ArgumentCaptor.forClass(List.class);
            verify(contestProblemMapper).batchInsert(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue().get(0).getContestId()).isEqualTo(CONTEST_ID);
        }
    }

    // ----------------------------------------------------------------------
    // deleteContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteContest — soft-delete (UPCOMING/FINISHED only)")
    class DeleteContest {

        @Test
        @DisplayName("throws CONTEST_NOT_FOUND when the contest does not exist")
        void missingContest_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.deleteContest(CONTEST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
        }

        @Test
        @DisplayName("throws CONTEST_NOT_FOUND when the contest is RUNNING (non-deletable)")
        void runningContest_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.RUNNING));

            assertThatThrownBy(() -> service.deleteContest(CONTEST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
        }

        @Test
        @DisplayName("soft-deletes a FINISHED contest, stamping deletedBy from the current user")
        void finishedContest_softDeletes() {
            Contest contest = buildContest(ContestStatus.FINISHED);
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_USER_ID);

            service.deleteContest(CONTEST_ID);

            verify(contestMapper).updateById(contest);
            assertThat(contest.getIsDeleted()).isTrue();
            assertThat(contest.getDeletedBy()).isEqualTo(ADMIN_USER_ID);
            assertThat(contest.getDeletedAt()).isNotNull();
        }
    }

    // ----------------------------------------------------------------------
    // startContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("startContest — UPCOMING -> RUNNING (requires >= 1 problem)")
    class StartContest {

        @Test
        @DisplayName("throws CONTEST_NOT_STARTED when the contest is not UPCOMING")
        void notUpcoming_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.RUNNING));

            assertThatThrownBy(() -> service.startContest(CONTEST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_STARTED);
        }

        @Test
        @DisplayName("throws CONTEST_NOT_FOUND when the contest has zero problems (start guard)")
        void zeroProblems_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.UPCOMING));
            when(contestReadPort.countProblemsByContestId(CONTEST_ID)).thenReturn(0L);

            assertThatThrownBy(() -> service.startContest(CONTEST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
        }

        @Test
        @DisplayName("transitions UPCOMING -> RUNNING and returns the projected VO")
        void happyPath_transitionsToRunning() {
            Contest contest = buildContest(ContestStatus.UPCOMING);
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            when(contestReadPort.countProblemsByContestId(CONTEST_ID)).thenReturn(3L);
            AdminContestVO vo = new AdminContestVO();
            when(adminContestProjection.toAdminVO(contest)).thenReturn(vo);

            AdminContestVO result = service.startContest(CONTEST_ID);

            assertThat(result).isSameAs(vo);
            assertThat(contest.getStatus()).isEqualTo(ContestStatus.RUNNING.name());
            verify(contestMapper).updateById(contest);
        }
    }

    // ----------------------------------------------------------------------
    // endContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("endContest — RUNNING -> FINISHED")
    class EndContest {

        @Test
        @DisplayName("throws CONTEST_ENDED when the contest is not RUNNING")
        void notRunning_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.UPCOMING));

            assertThatThrownBy(() -> service.endContest(CONTEST_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_ENDED);
        }

        @Test
        @DisplayName("transitions RUNNING -> FINISHED and returns the projected VO")
        void happyPath_transitionsToFinished() {
            Contest contest = buildContest(ContestStatus.RUNNING);
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
            AdminContestVO vo = new AdminContestVO();
            when(adminContestProjection.toAdminVO(contest)).thenReturn(vo);

            AdminContestVO result = service.endContest(CONTEST_ID);

            assertThat(result).isSameAs(vo);
            assertThat(contest.getStatus()).isEqualTo(ContestStatus.FINISHED.name());
        }
    }

    // ----------------------------------------------------------------------
    // Announcement CRUD
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("createAnnouncement — create + WebSocket push (D-12)")
    class CreateAnnouncement {

        @Test
        @DisplayName("throws CONTEST_NOT_FOUND when the contest does not exist")
        void missingContest_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.createAnnouncement(CONTEST_ID, "t", "c", false))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
            verifyNoInteractions(contestAnnouncementPushPort);
        }

        @Test
        @DisplayName("persists the announcement and pushes it via the push port")
        void happyPath_persistsAndPushes() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.RUNNING));

            ContestAnnouncement result = service.createAnnouncement(CONTEST_ID, "Hello", "World", true);

            ArgumentCaptor<ContestAnnouncement> rowCaptor = ArgumentCaptor.forClass(ContestAnnouncement.class);
            verify(contestAnnouncementMapper).insert(rowCaptor.capture());
            ContestAnnouncement saved = rowCaptor.getValue();
            assertThat(saved.getContestId()).isEqualTo(CONTEST_ID);
            assertThat(saved.getTitle()).isEqualTo("Hello");
            assertThat(saved.getIsPinned()).isTrue();

            ArgumentCaptor<AnnouncementPayload> payloadCaptor = ArgumentCaptor.forClass(AnnouncementPayload.class);
            verify(contestAnnouncementPushPort).emitAnnouncement(eq(CONTEST_ID), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().contestId()).isEqualTo(CONTEST_ID);

            assertThat(result.getContestId()).isEqualTo(CONTEST_ID);
        }

        @Test
        @DisplayName("null isPinned defaults to false on the persisted row")
        void nullIsPinned_defaultsFalse() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.RUNNING));

            service.createAnnouncement(CONTEST_ID, "t", "c", null);

            ArgumentCaptor<ContestAnnouncement> rowCaptor = ArgumentCaptor.forClass(ContestAnnouncement.class);
            verify(contestAnnouncementMapper).insert(rowCaptor.capture());
            assertThat(rowCaptor.getValue().getIsPinned()).isFalse();
        }
    }

    @Nested
    @DisplayName("updateAnnouncement — partial update")
    class UpdateAnnouncement {

        @Test
        @DisplayName("throws BAD_REQUEST when the announcement does not exist under the contest")
        void missingAnnouncement_throws() {
            when(contestAnnouncementMapper.findByContestIdAndId(CONTEST_ID, "ann-1")).thenReturn(null);

            assertThatThrownBy(() -> service.updateAnnouncement(CONTEST_ID, "ann-1", "t", "c", true))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
        }

        @Test
        @DisplayName("applies only the provided fields and persists the update")
        void happyPath_appliesProvidedFields() {
            ContestAnnouncement ann = new ContestAnnouncement();
            ann.setId("ann-1");
            ann.setContestId(CONTEST_ID);
            ann.setTitle("old");
            ann.setContent("old content");
            ann.setIsPinned(false);
            when(contestAnnouncementMapper.findByContestIdAndId(CONTEST_ID, "ann-1")).thenReturn(ann);

            ContestAnnouncement result = service.updateAnnouncement(CONTEST_ID, "ann-1", "new", null, true);

            verify(contestAnnouncementMapper).updateById(ann);
            assertThat(result.getTitle()).isEqualTo("new");
            assertThat(result.getContent()).isEqualTo("old content"); // unchanged
            assertThat(result.getIsPinned()).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteAnnouncement")
    class DeleteAnnouncement {

        @Test
        @DisplayName("throws BAD_REQUEST when the announcement does not exist under the contest")
        void missingAnnouncement_throws() {
            when(contestAnnouncementMapper.findByContestIdAndId(CONTEST_ID, "ann-1")).thenReturn(null);

            assertThatThrownBy(() -> service.deleteAnnouncement(CONTEST_ID, "ann-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
        }

        @Test
        @DisplayName("deletes the announcement row by id")
        void happyPath_deletesById() {
            ContestAnnouncement ann = new ContestAnnouncement();
            ann.setId("ann-1");
            ann.setContestId(CONTEST_ID);
            ann.setTitle("t");
            when(contestAnnouncementMapper.findByContestIdAndId(CONTEST_ID, "ann-1")).thenReturn(ann);

            service.deleteAnnouncement(CONTEST_ID, "ann-1");

            verify(contestAnnouncementMapper).deleteById("ann-1");
        }
    }

    // ----------------------------------------------------------------------
    // addProblemToContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("addProblemToContest — problem association")
    class AddProblem {

        @Test
        @DisplayName("throws CONTEST_NOT_FOUND when the contest does not exist")
        void missingContest_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.addProblemToContest(CONTEST_ID, 42L, 100))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
        }

        @Test
        @DisplayName("throws CONFLICT when the problem is already linked")
        void duplicateProblem_throws() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.UPCOMING));
            when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, 42L))
                    .thenReturn(new ContestProblem());

            assertThatThrownBy(() -> service.addProblemToContest(CONTEST_ID, 42L, 100))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
        }

        @Test
        @DisplayName("computes the next index from the problem count and inserts the link")
        void happyPath_insertsWithComputedIndex() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.UPCOMING));
            when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, 42L)).thenReturn(null);
            when(contestReadPort.countProblemsByContestId(CONTEST_ID)).thenReturn(5L);

            ContestProblem result = service.addProblemToContest(CONTEST_ID, 42L, 250);

            ArgumentCaptor<ContestProblem> captor = ArgumentCaptor.forClass(ContestProblem.class);
            verify(contestProblemMapper).insert(captor.capture());
            ContestProblem saved = captor.getValue();
            assertThat(saved.getContestId()).isEqualTo(CONTEST_ID);
            assertThat(saved.getProblemId()).isEqualTo(42L);
            assertThat(saved.getProblemIndex()).isEqualTo("Q6");
            assertThat(saved.getBaseScore()).isEqualTo(250);
            assertThat(result.getProblemIndex()).isEqualTo("Q6");
        }

        @Test
        @DisplayName("null score defaults to base score 100")
        void nullScore_defaultsTo100() {
            when(contestMapper.selectById(CONTEST_ID)).thenReturn(buildContest(ContestStatus.UPCOMING));
            when(contestProblemMapper.findByContestIdAndProblemId(eq(CONTEST_ID), anyLong())).thenReturn(null);
            when(contestReadPort.countProblemsByContestId(CONTEST_ID)).thenReturn(0L);

            service.addProblemToContest(CONTEST_ID, 42L, null);

            ArgumentCaptor<ContestProblem> captor = ArgumentCaptor.forClass(ContestProblem.class);
            verify(contestProblemMapper).insert(captor.capture());
            assertThat(captor.getValue().getBaseScore()).isEqualTo(100);
            assertThat(captor.getValue().getProblemIndex()).isEqualTo("Q1");
        }
    }

    // ----------------------------------------------------------------------
    // Test fixture helpers
    // ----------------------------------------------------------------------

    private static CreateContestDTO buildCreateDto(String title, LocalDateTime startTime,
                                                   Integer duration, Boolean isPublished,
                                                   List<Long> problemIds) {
        CreateContestDTO dto = new CreateContestDTO();
        dto.setTitle(title);
        dto.setStartTime(startTime);
        dto.setDuration(duration);
        dto.setIsPublished(isPublished);
        dto.setProblemIds(problemIds);
        return dto;
    }

    private static Contest buildContest(ContestStatus status) {
        Contest contest = new Contest();
        contest.setId(CONTEST_ID);
        contest.setTitle("Title");
        contest.setStatus(status.name());
        contest.setStartTime(LocalDateTime.of(2026, 7, 1, 10, 0));
        contest.setEndTime(LocalDateTime.of(2026, 7, 1, 13, 0));
        contest.setDurationMinutes(180);
        contest.setIsVisible(false);
        return contest;
    }
}
