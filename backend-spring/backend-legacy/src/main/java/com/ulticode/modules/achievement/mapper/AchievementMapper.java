package com.ulticode.modules.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.achievement.entity.Achievement;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


/**
 * Mapper for Achievement entity.
 */
@Mapper
public interface AchievementMapper extends BaseMapper<Achievement> {

    /**
     * Find achievement by key.
     *
     * <p>NOTE: This is intentionally a {@code default} method (not a
     * {@code @Select} annotation). Two reasons:
     * <ol>
     *   <li><b>CRITICAL #2</b> — the previous {@code @Select("SELECT * FROM
     *       achievements WHERE key = #{key}")} used an unquoted
     *       MySQL reserved word {@code key} and raised
     *       {@code SQLSyntaxErrorException}. Adding backticks fixed the SQL
     *       syntax, but kept the {@code @Select}.</li>
     *   <li><b>Bug #8</b> (discovered by {@code AchievementMapperIT}
     *       findByKey_criteriaIsDeserializedAsMap) — even with the SQL
     *       backticked, the custom {@code @Select} bypassed
     *       {@code @TableField(typeHandler = JacksonTypeHandler.class)} on
     *       {@code Achievement.criteria}, so the JSON column came back
     *       as {@code null} instead of a {@code Map}. Routing through
     *       {@code BaseMapper.selectList} with a {@code LIMIT 1} wrapper
     *       lets MyBatis-Plus honour the entity-level annotations.</li>
     * </ol>
     * </p>
     *
     * <p>Only caller in the codebase:
     * {@code AchievementServiceImpl.create} — uses the return value as a
     * duplicate-key check, never reads {@code criteria}. Fixing the
     * typeHandler is therefore a latent-defect cleanup, not a bug-blocking
     * change. But the test is fast and prevents future regression if a new
     * caller is added that <em>does</em> read {@code criteria}.</p>
     *
     * @param key the achievement key
     * @return the achievement or null
     */
    default Achievement findByKey(String key) {
        List<Achievement> rows = selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Achievement>()
                        .eq(Achievement::getKey, key)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Find all active achievements.
     *
     * <p>NOTE: This is intentionally NOT a {@code @Select} annotation. The
     * previous version used
     * {@code @Select("SELECT * FROM achievements WHERE is_active = 1 ORDER BY ...")}
     * which bypassed the {@code @TableField(typeHandler = JacksonTypeHandler.class)}
     * on {@code Achievement.criteria} — MyBatis returned {@code criteria} as
     * {@code null} (or a raw String), breaking {@code getUserAchievements}'
     * progress/target extraction. Routing through
     * {@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectList} lets
     * MyBatis-Plus honour the entity-level {@code @TableField} annotations
     * and apply {@code JacksonTypeHandler} for the JSON column.</p>
     *
     * <p>Discovered during implementation of
     * docs/.claude/PRPs/plans/achievement-api-fixes.plan.md Task 4 — T3
     * validation showed {@code progress=0, target=0} despite DB rows
     * containing valid JSON {@code {"type":"problems_solved","target":1}}.</p>
     */
    default List<Achievement> findAllActive() {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Achievement>()
                .eq(Achievement::getIsActive, true)
                .orderByAsc(Achievement::getCategory, Achievement::getTier));
    }
}
