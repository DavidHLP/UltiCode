package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.port.AdminContestReadPort;
import com.ulticode.modules.admin.port.ContestAnnouncementPushPort;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import com.ulticode.modules.contest.port.ContestOwnerPort;
import com.ulticode.app.api.dto.AnnouncementPayload;
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

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-OWNER-001-B: unit tests for {@link AdminContestMutationServiceImpl}
 * after the owner-write port seam.
 *
 * <p>The legacy test pinned the foreign-mapper write contract
 * (contestMapper.insert, contestProblemMapper.batchInsert,
 * contestAnnouncementMapper.insert / updateById / deleteById).
 * The new contract is the {@link ContestOwnerPort} boundary;
 * the test now stubs the port to return the expected ids and
 * asserts the port is called with the right commands, plus the
 * surviving read-path assertions (re-fetch via the projection,
 * existence checks, audit-context payloads, announcement push).
 *
 * <p>The status-guard, slug generation, problem shaping, and
 * write-mechanics are owned by the port; they have their own
 * contract tests in {@code DefaultContestOwnerPortTest} (added
 * separately).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminContestMutationServiceImpl (admin contest writes)")
class AdminContestMutationServiceImplTest {

    private static final String ADMIN_USER_ID = "admin-1";
    private static final String CONTEST_ID = "contest-1";

    /** P7-AC7: read-side ports replace the legacy mappers */
    @Mock private ContestAdminReadPort contestAdminReadPort;
    @Mock private ContestAnnouncementReadPort contestAnnouncementReadPort;
    @Mock private ContestAnnouncementPushPort contestAnnouncementPushPort;
    @Mock private AdminContestReadPort contestReadPort;
    @Mock private AdminContestProjection adminContestProjection;
    @Mock private CurrentUserProvider currentUserProvider;

    /**
     * P3-OWNER-001-B: the foreign write contract now flows through
     * this port. The test stubs the port to return the new ids.
     */
    @Mock private ContestOwnerPort contestOwnerPort;

    private final Clock clock = Clock.systemUTC();

    private AdminContestMutationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminContestMutationServiceImpl(
                contestAdminReadPort, contestAnnouncementReadPort,
                contestAnnouncementPushPort, contestReadPort,
                clock, adminContestProjection, currentUserProvider, contestOwnerPort);
        when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_USER_ID);
    }

    @AfterEach
    void tearDown() {
        // Service populates AuditContext ThreadLocals that the @Audited
        // aspect would clear in production but unit tests never invoke.
        AuditContext.clear();
    }

    // ----------------------------------------------------------------------
    // createContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("createContest — owner port seam")
    class CreateContest {

        @Test
        @DisplayName("forwards the DTO + userId to ContestOwnerPort.createContest and re-fetches via projection")
        void routesToOwnerPort() {
            when(contestOwnerPort.createContest(any(CreateContestDTO.class), eq(ADMIN_USER_ID)))
                    .thenReturn(CONTEST_ID);
            final AdminContestVO expected = new AdminContestVO();
            expected.setId(CONTEST_ID);
            when(adminContestProjection.getContest(CONTEST_ID)).thenReturn(expected);

            final AdminContestVO vo = service.createContest(new CreateContestDTO(), ADMIN_USER_ID);

            assertThat(vo).isSameAs(expected);
            final ArgumentCaptor<CreateContestDTO> cmdCaptor = ArgumentCaptor.forClass(CreateContestDTO.class);
            verify(contestOwnerPort).createContest(cmdCaptor.capture(), eq(ADMIN_USER_ID));
            assertThat(cmdCaptor.getValue()).isNotNull();
            // P3-OWNER-001-B: the admin no longer calls the foreign
            // mapper for the contest row insert.
            verify(contestOwnerPort).createContest(any(CreateContestDTO.class), eq(ADMIN_USER_ID));
        }

        @Test
        @DisplayName("surfaces owner-port CONTEST_SLUG_EXISTS as a BusinessException")
        void slugConflictFromPort() {
            when(contestOwnerPort.createContest(any(CreateContestDTO.class), anyString()))
                    .thenThrow(new BusinessException(ErrorCode.CONTEST_SLUG_EXISTS, "duplicate"));

            assertThatThrownBy(() -> service.createContest(new CreateContestDTO(), ADMIN_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_SLUG_EXISTS));
        }
    }

    // ----------------------------------------------------------------------
    // updateContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("updateContest — owner port seam")
    class UpdateContest {

        @Test
        @DisplayName("forwards id + DTO to ContestOwnerPort.updateContest and re-fetches via projection")
        void routesToOwnerPort() {
            final ContestAdminDTO before = new ContestAdminDTO();
            before.setId(CONTEST_ID);
            before.setTitle("Old");
            before.setStatus("UPCOMING");
            when(contestAdminReadPort.selectById(CONTEST_ID)).thenReturn(before);
            final AdminContestVO expected = new AdminContestVO();
            expected.setId(CONTEST_ID);
            when(adminContestProjection.getContest(CONTEST_ID)).thenReturn(expected);

            final AdminContestVO vo = service.updateContest(CONTEST_ID, new UpdateContestDTO());

            assertThat(vo).isSameAs(expected);
            verify(contestOwnerPort).updateContest(eq(CONTEST_ID), any(UpdateContestDTO.class));
            // P3-OWNER-001-B: the admin no longer calls the foreign
            // mapper for the contest row update or the problem
            // replacement.
            verify(contestOwnerPort).updateContest(anyString(), any(UpdateContestDTO.class));
        }

        @Test
        @DisplayName("CONTEST_NOT_FOUND when the contest does not exist")
        void notFound() {
            when(contestAdminReadPort.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> service.updateContest("missing", new UpdateContestDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_NOT_FOUND));
            verify(contestOwnerPort, never()).updateContest(anyString(), any());
        }

        @Test
        @DisplayName("CONTEST_ONLY_REGISTER_UPCOMING when the contest is not UPCOMING (raised by the port)")
        void notUpcoming() {
            final ContestAdminDTO before = new ContestAdminDTO();
            before.setId(CONTEST_ID);
            before.setTitle("Running contest");
            before.setStatus("RUNNING");
            when(contestAdminReadPort.selectById(CONTEST_ID)).thenReturn(before);
            // The port is the layer that owns the status guard; the
            // service delegates the write so the port raises the
            // exception. The test simulates that by having the port
            // throw on the matching call. updateContest is a void
            // return, so use doThrow instead of thenThrow.
            org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING))
                    .when(contestOwnerPort).updateContest(eq(CONTEST_ID), any(UpdateContestDTO.class));

            assertThatThrownBy(() -> service.updateContest(CONTEST_ID, new UpdateContestDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING));
        }
    }

    // ----------------------------------------------------------------------
    // deleteContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("deleteContest — owner port seam")
    class DeleteContest {

        @Test
        @DisplayName("forwards id + currentUserId to ContestOwnerPort.deleteContest")
        void routesToOwnerPort() {
            final ContestAdminDTO before = new ContestAdminDTO();
            before.setId(CONTEST_ID);
            before.setStatus("UPCOMING");
            when(contestAdminReadPort.selectById(CONTEST_ID)).thenReturn(before);

            service.deleteContest(CONTEST_ID);

            verify(contestOwnerPort).deleteContest(eq(CONTEST_ID), eq(ADMIN_USER_ID));
        }
    }

    // ----------------------------------------------------------------------
    // startContest / endContest
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("startContest — owner port seam")
    class StartContest {

        @Test
        @DisplayName("forwards id to ContestOwnerPort.startContest and re-fetches via projection")
        void routesToOwnerPort() {
            final ContestAdminDTO before = new ContestAdminDTO();
            before.setId(CONTEST_ID);
            before.setStatus("UPCOMING");
            when(contestAdminReadPort.selectById(CONTEST_ID)).thenReturn(before);
            final AdminContestVO expected = new AdminContestVO();
            expected.setId(CONTEST_ID);
            when(adminContestProjection.getContest(CONTEST_ID)).thenReturn(expected);

            final AdminContestVO vo = service.startContest(CONTEST_ID);

            assertThat(vo).isSameAs(expected);
            verify(contestOwnerPort).startContest(CONTEST_ID);
        }
    }

    @Nested
    @DisplayName("endContest — owner port seam")
    class EndContest {

        @Test
        @DisplayName("forwards id to ContestOwnerPort.endContest and re-fetches via projection")
        void routesToOwnerPort() {
            final ContestAdminDTO before = new ContestAdminDTO();
            before.setId(CONTEST_ID);
            before.setStatus("RUNNING");
            when(contestAdminReadPort.selectById(CONTEST_ID)).thenReturn(before);
            final AdminContestVO expected = new AdminContestVO();
            expected.setId(CONTEST_ID);
            when(adminContestProjection.getContest(CONTEST_ID)).thenReturn(expected);

            final AdminContestVO vo = service.endContest(CONTEST_ID);

            assertThat(vo).isSameAs(expected);
            verify(contestOwnerPort).endContest(CONTEST_ID);
        }
    }

    // ----------------------------------------------------------------------
    // Announcements
    // ----------------------------------------------------------------------

    @Nested
    @DisplayName("createAnnouncement — owner port seam")
    class CreateAnnouncement {

        @Test
        @DisplayName("forwards to ContestOwnerPort.createAnnouncement, then pushes via WebSocket")
        void routesAndPushes() {
            final String annId = "ann-1";
            when(contestOwnerPort.createAnnouncement(eq(CONTEST_ID), eq("t"), eq("c"), eq(true)))
                    .thenReturn(annId);
            final ContestAnnouncementDTO ann = new ContestAnnouncementDTO();
            ann.setId(annId);
            ann.setContestId(CONTEST_ID);
            ann.setTitle("t");
            ann.setContent("c");
            ann.setIsPinned(true);
            when(contestAnnouncementReadPort.findByContestIdAndId(CONTEST_ID, annId)).thenReturn(ann);

            final ContestAnnouncementDTO result = service.createAnnouncement(CONTEST_ID, "t", "c", true);

            assertThat(result).isSameAs(ann);
            verify(contestOwnerPort).createAnnouncement(CONTEST_ID, "t", "c", true);
            verify(contestAnnouncementPushPort).emitAnnouncement(eq(CONTEST_ID), any(AnnouncementPayload.class));
        }
    }

    @Nested
    @DisplayName("updateAnnouncement — owner port seam")
    class UpdateAnnouncement {

        @Test
        @DisplayName("forwards to ContestOwnerPort.updateAnnouncement and re-fetches via read port")
        void routesAndRefetches() {
            final ContestAnnouncementDTO before = new ContestAnnouncementDTO();
            before.setId("ann-1");
            before.setContestId(CONTEST_ID);
            before.setTitle("old");
            before.setIsPinned(false);
            final ContestAnnouncementDTO after = new ContestAnnouncementDTO();
            after.setId("ann-1");
            after.setContestId(CONTEST_ID);
            after.setTitle("new");
            after.setIsPinned(true);
            when(contestAnnouncementReadPort.findByContestIdAndId(CONTEST_ID, "ann-1"))
                    .thenReturn(before)
                    .thenReturn(after);

            final ContestAnnouncementDTO result = service.updateAnnouncement(
                    CONTEST_ID, "ann-1", "new", null, true);

            assertThat(result).isSameAs(after);
            verify(contestOwnerPort).updateAnnouncement(CONTEST_ID, "ann-1", "new", null, true);
        }

        @Test
        @DisplayName("BAD_REQUEST when the announcement does not exist")
        void notFound() {
            when(contestAnnouncementReadPort.findByContestIdAndId(CONTEST_ID, "missing"))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.updateAnnouncement(
                    CONTEST_ID, "missing", "new", null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.BAD_REQUEST));
            verify(contestOwnerPort, never()).updateAnnouncement(anyString(), anyString(),
                    anyString(), any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("deleteAnnouncement — owner port seam")
    class DeleteAnnouncement {

        @Test
        @DisplayName("forwards to ContestOwnerPort.deleteAnnouncement")
        void routesToOwnerPort() {
            final ContestAnnouncementDTO before = new ContestAnnouncementDTO();
            before.setId("ann-1");
            before.setContestId(CONTEST_ID);
            before.setTitle("t");
            when(contestAnnouncementReadPort.findByContestIdAndId(CONTEST_ID, "ann-1"))
                    .thenReturn(before);

            service.deleteAnnouncement(CONTEST_ID, "ann-1");

            verify(contestOwnerPort).deleteAnnouncement(CONTEST_ID, "ann-1");
        }
    }
}
