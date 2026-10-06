package com.ulticode.modules.learningplan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.learningplan.dto.LearningPlanVO;
import com.ulticode.modules.learningplan.dto.SaveLearningPlanDTO;
import com.ulticode.modules.learningplan.entity.LearningPlan;
import com.ulticode.modules.learningplan.mapper.LearningPlanMapper;
import com.ulticode.modules.learningplan.port.LearningPlanAccessPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * App-owned learning-plan domain service.
 *
 * <p>Stores the confirmed learning plan only. It never stores or judges the
 * submission itself and never writes any other owner's table.
 *
 * <p><b>Write contract.</b> Identity and source-submission ownership are
 * verified through {@link LearningPlanAccessPort} <em>before</em> the
 * transaction opens, so no cross-owner RPC is ever issued while the DB
 * transaction is held. The local transaction then:
 * <ol>
 *   <li>{@code INSERT ... ON DUPLICATE KEY UPDATE id = id} behind the
 *       {@code (user_id, idempotency_key)} unique key;</li>
 *   <li>current-reads the row with {@code SELECT ... FOR UPDATE};</li>
 *   <li>returns the stored row when the payload fingerprint matches (replay),
 *       or fails with {@code 40900} when the same key carries a different
 *       payload.</li>
 * </ol>
 * The stored row is never mutated after insert and the idempotency key is
 * never rotated.
 */
@Slf4j
public class LearningPlanService {

    private final LearningPlanMapper learningPlanMapper;
    private final TransactionTemplate transactionTemplate;
    private final LearningPlanAccessPort accessPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LearningPlanService(LearningPlanMapper learningPlanMapper,
                               TransactionTemplate transactionTemplate,
                               LearningPlanAccessPort accessPort,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.learningPlanMapper = learningPlanMapper;
        this.transactionTemplate = transactionTemplate;
        this.accessPort = accessPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Persist (or replay) a confirmed learning plan for {@code userId}.
     *
     * @param userId         authenticated principal; never caller-supplied
     * @param idempotencyKey canonical UUID from the {@code Idempotency-Key} header
     * @param dto            validated request body
     */
    public LearningPlanVO save(String userId, String idempotencyKey, SaveLearningPlanDTO dto) {
        if (transactionTemplate == null) {
            throw new BusinessException(BaseErrorCode.DATABASE_ERROR, "Transaction manager unavailable");
        }
        accessPort.requireActiveUser(userId);
        accessPort.requireOwnedSubmission(userId, dto.getSourceSubmissionId());

        String key = idempotencyKey.toLowerCase(Locale.ROOT);
        String sourceSubmissionId = dto.getSourceSubmissionId().toLowerCase(Locale.ROOT);
        String fingerprint = fingerprint(userId, sourceSubmissionId, dto);

        LearningPlan stored = transactionTemplate.execute(status -> {
            LearningPlan plan = new LearningPlan();
            plan.setId(UUID.randomUUID().toString());
            plan.setUserId(userId);
            plan.setIdempotencyKey(key);
            plan.setRequestFingerprint(fingerprint);
            plan.setSourceSubmissionId(sourceSubmissionId);
            plan.setDraftVersion(dto.getDraftVersion());
            plan.setTitle(dto.getTitle());
            plan.setContent(dto.getContent());
            plan.setCreatedAt(LocalDateTime.now(clock));

            learningPlanMapper.insertIfAbsent(plan);

            LearningPlan row = learningPlanMapper.selectByUserAndKeyForUpdate(userId, key);
            if (row == null) {
                // The unique-fence insert did not produce a readable row; never
                // report success without the authoritative current read.
                throw new BusinessException(BaseErrorCode.DATABASE_ERROR,
                        "Learning plan write did not persist");
            }
            if (!fingerprint.equals(row.getRequestFingerprint())) {
                throw new BusinessException(BaseErrorCode.CONFLICT, "idempotency_payload_mismatch");
            }
            return row;
        });

        log.info("Learning plan confirmed planId={}", stored.getId());
        return toVO(stored);
    }

    /** Owner-scoped read by plan id. */
    public LearningPlanVO get(String userId, String id) {
        accessPort.requireActiveUser(userId);
        LearningPlan row = learningPlanMapper.selectByIdAndUser(id, userId);
        if (row == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Learning plan not found");
        }
        return toVO(row);
    }

    /** Owner-scoped read by idempotency key. */
    public LearningPlanVO getByKey(String userId, String idempotencyKey) {
        accessPort.requireActiveUser(userId);
        LearningPlan row = learningPlanMapper.selectByUserAndKey(userId, idempotencyKey.toLowerCase(Locale.ROOT));
        if (row == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Learning plan not found");
        }
        return toVO(row);
    }

    /**
     * Fixed-order JSON array {@code [userId, sourceSubmissionId, draftVersion,
     * title, content]} hashed with SHA-256. Order and encoding are frozen so
     * Python and Java derive the same digest for the same payload.
     */
    private String fingerprint(String userId, String sourceSubmissionId, SaveLearningPlanDTO dto) {
        Object[] canonical = {
                userId,
                sourceSubmissionId,
                dto.getDraftVersion(),
                dto.getTitle(),
                dto.getContent()
        };
        try {
            byte[] json = objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                    "Unable to fingerprint learning plan payload", exception);
        }
    }

    private static LearningPlanVO toVO(LearningPlan plan) {
        LearningPlanVO vo = new LearningPlanVO();
        vo.setId(plan.getId());
        vo.setSourceSubmissionId(plan.getSourceSubmissionId());
        vo.setDraftVersion(plan.getDraftVersion());
        vo.setTitle(plan.getTitle());
        vo.setContent(plan.getContent());
        vo.setCreatedAt(plan.getCreatedAt());
        return vo;
    }
}
