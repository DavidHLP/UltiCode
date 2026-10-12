package com.ulticode.modules.learningplan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.learningplan.dto.LearningPlanVO;
import com.ulticode.modules.learningplan.dto.SaveLearningPlanDTO;
import com.ulticode.modules.learningplan.entity.LearningPlan;
import com.ulticode.modules.learningplan.mapper.LearningPlanMapper;
import com.ulticode.modules.learningplan.port.LearningPlanAccessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LearningPlanService}: idempotent replay, payload
 * mismatch, owner-scoped reads and fail-closed access checks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningPlanServiceTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_USER = "99999999-9999-9999-9999-999999999999";
    private static final String SUBMISSION = "22222222-2222-2222-2222-222222222222";
    private static final String KEY = "33333333-3333-3333-3333-333333333333";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-01T00:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private LearningPlanMapper learningPlanMapper;

    @Mock
    private LearningPlanAccessPort accessPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LearningPlanService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        lenient().when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        service = new LearningPlanService(
                learningPlanMapper, transactionTemplate, accessPort, objectMapper, FIXED_CLOCK);
    }

    private static SaveLearningPlanDTO dto() {
        SaveLearningPlanDTO dto = new SaveLearningPlanDTO();
        dto.setSourceSubmissionId(SUBMISSION.toUpperCase(Locale.ROOT));
        dto.setDraftVersion(1);
        dto.setTitle("Two pointers plan");
        dto.setContent("Read the statement, then mirror the indices.");
        return dto;
    }

    private String expectedFingerprint() {
        try {
            byte[] json = objectMapper.writeValueAsBytes(new Object[]{
                    USER, SUBMISSION, 1, "Two pointers plan",
                    "Read the statement, then mirror the indices."});
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static LearningPlan storedRow(String id, String fingerprint, LocalDateTime createdAt) {
        LearningPlan row = new LearningPlan();
        row.setId(id);
        row.setUserId(USER);
        row.setIdempotencyKey(KEY);
        row.setRequestFingerprint(fingerprint);
        row.setSourceSubmissionId(SUBMISSION);
        row.setDraftVersion(1);
        row.setTitle("Two pointers plan");
        row.setContent("Read the statement, then mirror the indices.");
        row.setCreatedAt(createdAt);
        return row;
    }

    private static int codeOf(BusinessException exception) {
        return exception.getCode();
    }

    @Test
    @DisplayName("first save inserts once, fingerprints the canonical payload and returns the row")
    void firstSavePersistsCanonicalRow() {
        AtomicReference<LearningPlan> inserted = new AtomicReference<>();
        when(learningPlanMapper.insertIfAbsent(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(learningPlanMapper.selectByUserAndKeyForUpdate(USER, KEY))
                .thenAnswer(invocation -> inserted.get());

        LearningPlanVO vo = service.save(USER, KEY, dto());

        LearningPlan stored = inserted.get();
        assertThat(stored.getUserId()).isEqualTo(USER);
        assertThat(stored.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(stored.getSourceSubmissionId()).isEqualTo(SUBMISSION);
        assertThat(stored.getRequestFingerprint()).isEqualTo(expectedFingerprint());
        assertThat(vo.getId()).isEqualTo(stored.getId());
        assertThat(vo.getSourceSubmissionId()).isEqualTo(SUBMISSION);
        assertThat(vo.getDraftVersion()).isEqualTo(1);
        assertThat(vo.getTitle()).isEqualTo("Two pointers plan");
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
        verify(accessPort).requireActiveUser(USER);
        verify(accessPort).requireOwnedSubmission(USER, SUBMISSION.toUpperCase(Locale.ROOT));
    }

    @Test
    @DisplayName("replaying the same key and payload returns the original row")
    void replayReturnsOriginalRow() {
        LocalDateTime originalCreatedAt = LocalDateTime.now(FIXED_CLOCK).minusMinutes(5);
        when(learningPlanMapper.insertIfAbsent(any())).thenReturn(0);
        when(learningPlanMapper.selectByUserAndKeyForUpdate(USER, KEY))
                .thenReturn(storedRow("existing-plan-id", expectedFingerprint(), originalCreatedAt));

        LearningPlanVO vo = service.save(USER, KEY, dto());

        assertThat(vo.getId()).isEqualTo("existing-plan-id");
        assertThat(vo.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    @DisplayName("reusing the key with a different payload fails with 40900")
    void keyReuseWithDifferentPayloadConflicts() {
        when(learningPlanMapper.insertIfAbsent(any())).thenReturn(0);
        when(learningPlanMapper.selectByUserAndKeyForUpdate(USER, KEY))
                .thenReturn(storedRow("existing-plan-id", "f".repeat(64), LocalDateTime.now(FIXED_CLOCK)));

        BusinessException exception = catchThrowableOfType(
                () -> service.save(USER, KEY, dto()), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo("idempotency_payload_mismatch");
        assertThat(codeOf(exception)).isEqualTo(BaseErrorCode.CONFLICT.code());
    }

    @Test
    @DisplayName("access denial aborts before any local write")
    void accessDenialAbortsBeforeWrite() {
        doThrow(new BusinessException(BaseErrorCode.FORBIDDEN, "Account is banned"))
                .when(accessPort).requireActiveUser(USER);

        BusinessException exception = catchThrowableOfType(
                () -> service.save(USER, KEY, dto()), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(codeOf(exception)).isEqualTo(BaseErrorCode.FORBIDDEN.code());
        verifyNoInteractions(learningPlanMapper);
    }

    @Test
    @DisplayName("source submission denial aborts before any local write")
    void submissionDenialAbortsBeforeWrite() {
        doThrow(new BusinessException(BaseErrorCode.NOT_FOUND, "Source submission not found"))
                .when(accessPort).requireOwnedSubmission(USER, SUBMISSION.toUpperCase(Locale.ROOT));

        BusinessException exception = catchThrowableOfType(
                () -> service.save(USER, KEY, dto()), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(codeOf(exception)).isEqualTo(BaseErrorCode.NOT_FOUND.code());
        verify(accessPort).requireActiveUser(USER);
        verifyNoInteractions(learningPlanMapper);
    }

    @Test
    @DisplayName("get returns the owner-scoped row")
    void getReturnsOwnedRow() {
        when(learningPlanMapper.selectByIdAndUser("plan-1", USER))
                .thenReturn(storedRow("plan-1", expectedFingerprint(), LocalDateTime.now(FIXED_CLOCK)));

        LearningPlanVO vo = service.get(USER, "plan-1");

        assertThat(vo.getId()).isEqualTo("plan-1");
        assertThat(vo.getDraftVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("get hides another user's row as not found")
    void getHidesForeignRow() {
        when(learningPlanMapper.selectByIdAndUser("plan-1", OTHER_USER)).thenReturn(null);

        BusinessException exception = catchThrowableOfType(
                () -> service.get(OTHER_USER, "plan-1"), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(codeOf(exception)).isEqualTo(BaseErrorCode.NOT_FOUND.code());
    }

    @Test
    @DisplayName("getByKey normalises the key to lower case")
    void getByKeyNormalisesKey() {
        when(learningPlanMapper.selectByUserAndKey(USER, KEY))
                .thenReturn(storedRow("plan-1", expectedFingerprint(), LocalDateTime.now(FIXED_CLOCK)));

        LearningPlanVO vo = service.getByKey(USER, KEY.toUpperCase(Locale.ROOT));

        assertThat(vo.getId()).isEqualTo("plan-1");
        verify(learningPlanMapper).selectByUserAndKey(USER, KEY);
    }

    @Test
    @DisplayName("getByKey is owner scoped and hides unknown keys")
    void getByKeyHidesUnknownKey() {
        when(learningPlanMapper.selectByUserAndKey(anyString(), anyString())).thenReturn(null);

        BusinessException exception = catchThrowableOfType(
                () -> service.getByKey(USER, KEY), BusinessException.class);

        assertThat(exception).isNotNull();
        assertThat(codeOf(exception)).isEqualTo(BaseErrorCode.NOT_FOUND.code());
        verify(learningPlanMapper, never()).selectByIdAndUser(anyString(), anyString());
    }
}
