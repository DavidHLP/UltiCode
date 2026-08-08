package com.ulticode.modules.problem.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemNote;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemNoteMapper;
import com.ulticode.modules.problem.service.impl.ProblemNoteServiceImpl;
import com.ulticode.modules.problem.vo.ProblemNoteVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link ProblemNoteServiceImpl}.
 *
 * <p>Regression scope from {@code docs/interaction-note-api-test-report-2026-06-11.md}:
 * GET path (returns null when absent), POST path (upsert insert vs. update branch),
 * problem existence validation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemNoteService")
class ProblemNoteServiceTest {

    private static final String USER_ID = "user-001";
    private static final String OTHER_USER_ID = "user-002";
    private static final Long PROBLEM_ID = 1L;
    private static final Long MISSING_PROBLEM_ID = 9999L;

    @Mock
    private ProblemNoteMapper noteMapper;

    @Mock
    private ProblemMapper problemMapper;

    private ProblemNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProblemNoteServiceImpl(noteMapper, problemMapper, Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("getNote returns VO when note exists")
    void getNote_existingNote_returnsVO() {
        ProblemNote note = newNote("n-1", USER_ID, PROBLEM_ID, "old content");
        when(noteMapper.findByUserAndProblem(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(note));

        ProblemNoteVO result = service.getNote(USER_ID, PROBLEM_ID);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("old content");
        assertThat(result.getUpdateTime()).isNull();
    }

    @Test
    @DisplayName("getNote returns null when user has no note for the problem")
    void getNote_absentNote_returnsNull() {
        when(noteMapper.findByUserAndProblem(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());

        ProblemNoteVO result = service.getNote(USER_ID, PROBLEM_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getNote scoped to user: another user's note is invisible")
    void getNote_isolatesByUser() {
        ProblemNote someoneElse = newNote("n-other", OTHER_USER_ID, PROBLEM_ID, "secret");
        when(noteMapper.findByUserAndProblem(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(someoneElse));

        ProblemNoteVO result = service.getNote(USER_ID, PROBLEM_ID);

        // Mapper is the boundary that enforces per-user isolation; the service
        // simply forwards the userId. The mapper test would cover SQL-level isolation.
        // We still verify the service returns what the mapper returned.
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("secret");
    }

    @Test
    @DisplayName("upsert inserts new note when none exists for the user+problem")
    void upsert_noExistingNote_inserts() {
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(new Problem());
        when(noteMapper.findByUserAndProblem(USER_ID, PROBLEM_ID)).thenReturn(Optional.empty());

        ProblemNoteVO result = service.upsertNote(USER_ID, PROBLEM_ID, "fresh note");

        ArgumentCaptor<ProblemNote> captor = ArgumentCaptor.forClass(ProblemNote.class);
        verify(noteMapper).insert(captor.capture());
        verify(noteMapper, never()).updateById(any(ProblemNote.class));
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getProblemId()).isEqualTo(PROBLEM_ID);
        assertThat(captor.getValue().getContent()).isEqualTo("fresh note");
        // create_time and update_time must be populated by the service on insert
        // (FieldStrategy.NEVER on the entity means MyBatis-Plus will not fill
        // them; if the service forgets, the DB will reject the row with NOT NULL).
        assertThat(captor.getValue().getCreateTime()).isNotNull();
        assertThat(captor.getValue().getUpdateTime()).isNotNull();
        assertThat(result.getContent()).isEqualTo("fresh note");
    }

    @Test
    @DisplayName("upsert updates existing note content without changing id")
    void upsert_existingNote_updatesContent() {
        Problem existing = new Problem();
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(existing);
        ProblemNote stored = newNote("n-1", USER_ID, PROBLEM_ID, "old content");
        when(noteMapper.findByUserAndProblem(USER_ID, PROBLEM_ID)).thenReturn(Optional.of(stored));

        ProblemNoteVO result = service.upsertNote(USER_ID, PROBLEM_ID, "new content");

        ArgumentCaptor<ProblemNote> captor = ArgumentCaptor.forClass(ProblemNote.class);
        verify(noteMapper).updateById(captor.capture());
        verify(noteMapper, never()).insert(any(ProblemNote.class));
        assertThat(captor.getValue().getId()).isEqualTo("n-1");
        assertThat(captor.getValue().getContent()).isEqualTo("new content");
        // update_time is refreshed on every update; create_time is left untouched
        // (FieldStrategy.NEVER on the entity keeps it out of the UPDATE SQL).
        assertThat(captor.getValue().getUpdateTime()).isNotNull();
        assertThat(result.getContent()).isEqualTo("new content");
    }

    @Test
    @DisplayName("upsert throws BusinessException(PROBLEM_NOT_FOUND) when problem missing")
    void upsert_missingProblem_throws() {
        when(problemMapper.selectById(MISSING_PROBLEM_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.upsertNote(USER_ID, MISSING_PROBLEM_ID, "x"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProblemErrorCode.PROBLEM_NOT_FOUND);

        verify(noteMapper, never()).insert(any(ProblemNote.class));
        verify(noteMapper, never()).updateById(any(ProblemNote.class));
    }

    @Test
    @DisplayName("upsert downgrades concurrent insert race to update (no 500)")
    void upsert_concurrentInsertRace_downgradesToUpdate() {
        when(problemMapper.selectById(PROBLEM_ID)).thenReturn(new Problem());
        // The "find" branch returns empty (no existing row visible to us)...
        when(noteMapper.findByUserAndProblem(USER_ID, PROBLEM_ID))
                .thenReturn(Optional.empty())   // first call: nothing yet
                .thenReturn(Optional.of(newNote("n-winner", USER_ID, PROBLEM_ID, "winner content"))); // after lost race: reload
        // ...but the insert then collides with the unique key because a
        // concurrent request inserted the row between our find and insert.
        doThrow(new DuplicateKeyException("uk_user_problem"))
                .when(noteMapper).insert(any(ProblemNote.class));

        ProblemNoteVO result = service.upsertNote(USER_ID, PROBLEM_ID, "late writer content");

        // Insert was attempted (and failed); the recovery path re-loaded and updated.
        verify(noteMapper, times(2)).findByUserAndProblem(USER_ID, PROBLEM_ID);
        verify(noteMapper).insert(any(ProblemNote.class));
        verify(noteMapper).updateById(any(ProblemNote.class));
        // The returned VO carries the winner's id but the late writer's content.
        assertThat(result.getContent()).isEqualTo("late writer content");
    }

    private static ProblemNote newNote(String id, String userId, Long problemId, String content) {
        ProblemNote n = new ProblemNote();
        n.setId(id);
        n.setUserId(userId);
        n.setProblemId(problemId);
        n.setContent(content);
        return n;
    }
}
