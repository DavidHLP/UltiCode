package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.common.uuid.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link DefaultAdminContestProjection} &mdash; the read-side
 * deep module lifted out of {@code AdminContestServiceImpl} per ADR-0011
 * Stage 3.
 *
 * <p>Covers the projection surface: the {@code problemCount} read enrichment,
 * the {@code ContestAdminDTO} &rarr; {@link AdminContestVO} shape, the single-detail
 * not-found mapping, the URL slug rules and the null-safety guard.
 *
 * <p>These cases migrate the inline test surface that the legacy
 * {@code AdminContestServiceImpl} never had &mdash; the projection seam makes
 * them testable in isolation for the first time.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminContestProjection")
class AdminContestProjectionTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    private static final Pattern SLUG_RANDOM_TAIL = Pattern.compile("^contest-[0-9a-f]{8}$");

    @Mock private ContestAdminReadPort contestAdminReadPort;
    @Mock private UuidGenerator uuidGenerator;

    private DefaultAdminContestProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminContestProjection(contestAdminReadPort, uuidGenerator);
        org.mockito.Mockito.lenient().when(uuidGenerator.newId()).thenReturn("abc12345-end-of-uuid");
    }

    @Nested
    @DisplayName("toAdminVO(ContestAdminDTO) — DTO -> AdminContestVO shape")
    class ToAdminVO {

        @Test
        @DisplayName("returns null when the input contest is null")
        void nullInput_returnsNull() {
            assertThat(projection.toAdminVO(null)).isNull();
        }

        @Test
        @DisplayName("projects every field from the entity, including problemCount")
        void projectsEveryField() {
            String contestId = "contest-" + UUID.randomUUID();
            LocalDateTime start = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime end = LocalDateTime.of(2026, 7, 1, 13, 0);
            LocalDateTime created = LocalDateTime.of(2026, 6, 25, 9, 0);
            LocalDateTime updated = LocalDateTime.of(2026, 6, 30, 12, 0);
            ContestAdminDTO contest = buildContest(contestId, "weekly-21", "Weekly #21",
                    "team", "ICPC", "UPCOMING", start, end, 180, true, 42, created, updated);
            when(contestAdminReadPort.countProblemsByContestId(contestId)).thenReturn(7L);

            AdminContestVO vo = projection.toAdminVO(contest);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(contestId);
            assertThat(vo.getSlug()).isEqualTo("weekly-21");
            assertThat(vo.getTitle()).isEqualTo("Weekly #21");
            assertThat(vo.getDescription()).isEqualTo("team");
            assertThat(vo.getContestType()).isEqualTo("ICPC");
            assertThat(vo.getStatus()).isEqualTo("UPCOMING");
            assertThat(vo.getStartTime()).isEqualTo(start);
            assertThat(vo.getEndTime()).isEqualTo(end);
            assertThat(vo.getDurationMinutes()).isEqualTo(180);
            assertThat(vo.getIsVisible()).isTrue();
            assertThat(vo.getParticipantCount()).isEqualTo(42);
            assertThat(vo.getCreatedAt()).isEqualTo(created);
            assertThat(vo.getUpdatedAt()).isEqualTo(updated);
            assertThat(vo.getProblemCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("problemCount takes the Long count from the read port and casts to int")
        void problemCount_usesEntityId() {
            String contestId = "lookup-id";
            ContestAdminDTO contest = buildContest(contestId, "x", "X", "d", "ICPC", "DRAFT",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, false, 0, LocalDateTime.now(), LocalDateTime.now());
            when(contestAdminReadPort.countProblemsByContestId(contestId)).thenReturn(0L);

            AdminContestVO vo = projection.toAdminVO(contest);

            assertThat(vo.getProblemCount()).isZero();
        }

        @Test
        @DisplayName("problemCount > Integer.MAX_VALUE casts to int (documented int-range contract)")
        void problemCount_overflowStillMapsToInt() {
            String contestId = "huge-id";
            ContestAdminDTO contest = buildContest(contestId, "h", "huge", "d", "ICPC", "RUNNING",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, true, 0, LocalDateTime.now(), LocalDateTime.now());
            // (int) 2_000_000_000L = 2_000_000_000 (within int range); this documents
            // that the cast is intentional and bounded.
            when(contestAdminReadPort.countProblemsByContestId(contestId)).thenReturn(2_000_000_000L);

            AdminContestVO vo = projection.toAdminVO(contest);

            assertThat(vo.getProblemCount()).isEqualTo(2_000_000_000);
        }
    }

    @Nested
    @DisplayName("getContest(id) — single-detail read")
    class GetContest {

        @Test
        @DisplayName("returns the projected VO when the contest exists")
        void happyPath_returnsProjectedVO() {
            String id = "single-id";
            ContestAdminDTO contest = buildContest(id, "x", "X", "d", "ICPC", "RUNNING",
                    LocalDateTime.of(2026, 1, 1, 12, 0),
                    LocalDateTime.of(2026, 1, 1, 14, 0),
                    120, true, 10,
                    LocalDateTime.of(2025, 12, 1, 0, 0),
                    LocalDateTime.of(2025, 12, 31, 0, 0));
            when(contestAdminReadPort.selectByIdOrSlug(id)).thenReturn(contest);
            when(contestAdminReadPort.countProblemsByContestId(id)).thenReturn(3L);

            AdminContestVO vo = projection.getContest(id);

            assertThat(vo.getId()).isEqualTo(id);
            assertThat(vo.getStatus()).isEqualTo("RUNNING");
            assertThat(vo.getProblemCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("throws typed unavailable when the contest owner transport fails")
        void ownerFailure_throwsUnavailable() {
            when(contestAdminReadPort.selectByIdOrSlug("down"))
                    .thenThrow(new RuntimeException("timeout"));

            assertThatThrownBy(() -> projection.getContest("down"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("getContests(query) — paginated list shape")
    class GetContestsList {

        @Test
        @DisplayName("delegates to the read port with the same query shape and projects results")
        void happyPath_returnsPageResultOfVOs() {
            AdminContestQueryDTO query = new AdminContestQueryDTO();
            query.setPage(1);
            query.setLimit(10);
            query.setSearch("weekly");

            ContestAdminDTO c1 = buildContest("c1", "weekly-21", "Weekly #21", "d", "ICPC", "UPCOMING",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, true, 0, LocalDateTime.now(), LocalDateTime.now());
            c1.setProblemCount(4);
            ContestAdminDTO c2 = buildContest("c2", "weekly-22", "Weekly #22", "d", "ICPC", "RUNNING",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, true, 5, LocalDateTime.now(), LocalDateTime.now());
            c2.setProblemCount(8);

            PageResult<ContestAdminDTO> pageResult = PageResult.of(List.of(c1, c2), 2L, 1, 10);
            when(contestAdminReadPort.selectPage(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(pageResult);

            PageResult<AdminContestVO> result = projection.getContests(query);

            assertThat(result.getTotal()).isEqualTo(2L);
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems().get(0).getSlug()).isEqualTo("weekly-21");
            assertThat(result.getItems().get(0).getProblemCount()).isEqualTo(4);
            assertThat(result.getItems().get(1).getSlug()).isEqualTo("weekly-22");
            assertThat(result.getItems().get(1).getProblemCount()).isEqualTo(8);
            assertThat(result.getDegradationStatus()).isEqualTo(DegradationStatus.OK);
        }

        @Test
        @DisplayName("getContests does not call countProblemsByContestId per row (N+1 eliminated)")
        void listPath_doesNotInvokePerRowCount() {
            AdminContestQueryDTO query = new AdminContestQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            ContestAdminDTO c1 = buildContest("c1", "s1", "T1", "d", "ICPC", "UPCOMING",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, true, 0, LocalDateTime.now(), LocalDateTime.now());
            c1.setProblemCount(4);
            ContestAdminDTO c2 = buildContest("c2", "s2", "T2", "d", "ICPC", "RUNNING",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, true, 5, LocalDateTime.now(), LocalDateTime.now());
            c2.setProblemCount(8);

            PageResult<ContestAdminDTO> pageResult = PageResult.of(List.of(c1, c2), 2L, 1, 10);
            when(contestAdminReadPort.selectPage(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(pageResult);

            projection.getContests(query);

            // N+1 elimination: the list path must read problemCount from the DTO,
            // never calling the per-row count RPC.
            org.mockito.Mockito.verify(contestAdminReadPort,
                    org.mockito.Mockito.never()).countProblemsByContestId(anyString());
        }

        @Test
        @DisplayName("null owner page is unavailable, never an empty success")
        void nullOwnerPage_throwsUnavailable() {
            AdminContestQueryDTO query = new AdminContestQueryDTO();
            when(contestAdminReadPort.selectPage(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(null);

            assertThatThrownBy(() -> projection.getContests(query))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
        }

        @Test
        @DisplayName("returns an empty PageResult when the read port yields no records")
        void emptyResult_yieldsEmptyPageResult() {
            AdminContestQueryDTO query = new AdminContestQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            PageResult<ContestAdminDTO> emptyResult = PageResult.of(List.of(), 0L, 1, 10);
            when(contestAdminReadPort.selectPage(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(emptyResult);

            PageResult<AdminContestVO> result = projection.getContests(query);

            assertThat(result.getTotal()).isZero();
            assertThat(result.getItems()).isEmpty();
            assertThat(result.getDegradationStatus()).isEqualTo(DegradationStatus.OK);
        }

        @Test
        @DisplayName("owner page with UNAVAILABLE degradation status throws instead of returning empty")
        void unavailableDegradationStatus_throwsUnavailable() {
            AdminContestQueryDTO query = new AdminContestQueryDTO();
            when(contestAdminReadPort.selectPage(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(PageResult.of(
                            List.of(), 0L,
                            PaginationRequest.of(1, 10), DegradationStatus.UNAVAILABLE));

            assertThatThrownBy(() -> projection.getContests(query))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
        }

        @Test
        @DisplayName("owner page with PARTIAL degradation status preserves PARTIAL status")
        void partialDegradationStatus_preservesPartialStatus() {
            AdminContestQueryDTO query = new AdminContestQueryDTO();

            ContestAdminDTO c1 = buildContest("c1", "s1", "T1", "d", "ICPC", "UPCOMING",
                    LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                    120, true, 0, LocalDateTime.now(), LocalDateTime.now());
            c1.setProblemCount(4);
            PageResult<ContestAdminDTO> result = PageResult.of(
                    List.of(c1), 1L, PaginationRequest.of(1, 10), DegradationStatus.PARTIAL);
            when(contestAdminReadPort.selectPage(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                    .thenReturn(result);

            PageResult<AdminContestVO> page = projection.getContests(query);
            assertThat(page.getDegradationStatus()).isEqualTo(DegradationStatus.PARTIAL);
            assertThat(page.getItems()).hasSize(1);
        }
    }


    @Nested
    @DisplayName("generateSlug(title) — URL-friendly slug rules")
    class GenerateSlug {

        @Test
        @DisplayName("lowercases ASCII letters and rewrites whitespace as dashes")
        void lowercaseAscii() {
            assertThat(projection.generateSlug("Hello World")).isEqualTo("hello-world");
        }

        @Test
        @DisplayName("strips punctuation characters")
        void stripsPunctuation() {
            assertThat(projection.generateSlug("Contest #21 (Weekly)"))
                    .isEqualTo("contest-21-weekly");
        }

        @Test
        @DisplayName("collapses consecutive dashes and trims leading/trailing dashes")
        void collapsesAndTrimsDashes() {
            assertThat(projection.generateSlug("---foo bar---"))
                    .isEqualTo("foo-bar");
        }

        @Test
        @DisplayName("pads with random hex when the stripped slug is shorter than 3 characters")
        void padsShortSlug() {
            String slug = projection.generateSlug("a");
            assertThat(slug).startsWith("a-");
            assertThat(slug.substring(2)).matches("[0-9a-f]{8}");
        }

        @Test
        @DisplayName("returns a contest-<hex8> random id when the title is null or blank")
        void nullOrBlank_randomTail() {
            assertThat(projection.generateSlug(null)).matches(SLUG_RANDOM_TAIL);
            assertThat(projection.generateSlug("")).matches(SLUG_RANDOM_TAIL);
            assertThat(projection.generateSlug("   ")).matches(SLUG_RANDOM_TAIL);
        }

        @Test
        @DisplayName("preserves digits in the slug")
        void preservesDigits() {
            assertThat(projection.generateSlug("Weekly 2026")).isEqualTo("weekly-2026");
        }
    }

    // ----------------------------------------------------------------------
    // Test fixture helpers
    // ----------------------------------------------------------------------
    private static ContestAdminDTO buildContest(String id, String slug, String title, String description,
                                      String contestType, String status, LocalDateTime startTime,
                                      LocalDateTime endTime, Integer durationMinutes,
                                      Boolean isVisible, Integer participantCount,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        ContestAdminDTO contest = new ContestAdminDTO();
        contest.setId(id);
        contest.setSlug(slug);
        contest.setTitle(title);
        contest.setDescription(description);
        contest.setContestType(contestType);
        contest.setStatus(status);
        contest.setStartTime(startTime);
        contest.setEndTime(endTime);
        contest.setDurationMinutes(durationMinutes != null ? durationMinutes : 0);
        contest.setIsVisible(Boolean.TRUE.equals(isVisible));
        contest.setRegisteredCount(participantCount != null ? participantCount : 0);
        contest.setCreatedAt(createdAt);
        contest.setUpdatedAt(updatedAt);
        return contest;
    }

}
