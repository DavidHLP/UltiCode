package com.ulticode.modules.learningplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.learningplan.entity.LearningPlan;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for the App-owned {@code learning_plans} table.
 *
 * <p>The uniqueness fence is the {@code (user_id, idempotency_key)} unique key,
 * not an application-level check. {@link #insertIfAbsent(LearningPlan)}
 * swallows the duplicate-key error so the transaction can then read the
 * current row under {@code FOR UPDATE} and decide replay vs. payload mismatch.
 * All reads are owner-scoped; there is no finder that can return another
 * user's row.
 */
@Mapper
public interface LearningPlanMapper extends BaseMapper<LearningPlan> {

    /**
     * Insert the row, or no-op when the {@code (user_id, idempotency_key)}
     * unique key already exists. The transaction's subsequent
     * {@link #selectByUserAndKeyForUpdate} is the authoritative read, so a
     * concurrent insert cannot make this caller report a false success.
     */
    @Insert("INSERT INTO learning_plans "
            + "(id, user_id, idempotency_key, request_fingerprint, source_submission_id, "
            + "draft_version, title, content, created_at) "
            + "VALUES (#{id}, #{userId}, #{idempotencyKey}, #{requestFingerprint}, "
            + "#{sourceSubmissionId}, #{draftVersion}, #{title}, #{content}, #{createdAt}) "
            + "ON DUPLICATE KEY UPDATE id = id")
    int insertIfAbsent(LearningPlan plan);

    /**
     * Current-read the stored row for the key inside an open transaction.
     * {@code FOR UPDATE} closes the repeatable-read snapshot gap where a
     * competing insert could otherwise stay invisible after our duplicate-key
     * no-op.
     */
    @Select("SELECT * FROM learning_plans "
            + "WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey} FOR UPDATE")
    LearningPlan selectByUserAndKeyForUpdate(@Param("userId") String userId,
                                             @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT * FROM learning_plans "
            + "WHERE user_id = #{userId} AND idempotency_key = #{idempotencyKey}")
    LearningPlan selectByUserAndKey(@Param("userId") String userId,
                                    @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT * FROM learning_plans WHERE id = #{id} AND user_id = #{userId}")
    LearningPlan selectByIdAndUser(@Param("id") String id, @Param("userId") String userId);
}
