package com.ulticode.modules.achievement;

import com.baomidou.mybatisplus.annotation.TableField;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile-time + reflection regression guard for {@code achievements.key}
 * (MySQL reserved word).
 *
 * <p>Background: {@code key} is a MySQL reserved word, so any SQL that
 * references the column unquoted fails with
 * {@code SQLSyntaxErrorException: ... near 'key,...' at line 1}. This
 * affected:</p>
 * <ul>
 *   <li>{@link AchievementMapper#findByKey} — custom {@code @Select} SQL
 *       with {@code WHERE key = #{key}} — now uses {@code WHERE \`key\` = #{key}}</li>
 *   <li>MyBatis-Plus auto-generated column lists in {@code selectById} /
 *       {@code selectPage} / {@code selectBatchIds} (rendered as
 *       {@code SELECT id,key,name,...} — now backticked via
 *       {@code @TableField(value = "`key`")} on the entity field)</li>
 * </ul>
 *
 * <p>This test prevents the regression by asserting both:</p>
 * <ol>
 *   <li>{@link AchievementMapper#findByKey}'s {@code @Select} SQL body
 *       contains a backticked {@code `key`}</li>
 *   <li>{@link Achievement#key} has a {@code @TableField(value = "`key`")}
 *       annotation overriding the default column name</li>
 * </ol>
 *
 * <p>A full Testcontainers IT (real MySQL 9.1 {@code SELECT * FROM
 * achievements WHERE `key` = 'first_solved'}) would catch any runtime-only
 * regression and should be added as a follow-up; for now this guard runs
 * in <1ms and is sufficient for the structural bug class.
 * (Reported in docs/achievement-api-test-report-2026-06-11.md §6
 * CRITICAL #1/#2.)</p>
 *
 * <p>Pattern mirrors
 * {@code com.ulticode.modules.notification.mapper.NotificationMapperBatchInsertTest}.</p>
 */
@DisplayName("AchievementMapper#findByKey + Achievement.key reserved-word guard")
class AchievementMapperSQLGuardTest {

    @Test
    @DisplayName("findByKey does NOT use @Select (must go through BaseMapper for typeHandler — Bug #8)")
    void findByKeyDoesNotUseSelectAnnotation() throws NoSuchMethodException {
        // Bug #8: previous version had @Select with backticks (fixing CRITICAL #2 SQL
        // syntax) but the @Select still bypassed JacksonTypeHandler for the SELECT
        // column list, returning criteria as null. Now also a default method.
        Method method = AchievementMapper.class.getDeclaredMethod("findByKey", String.class);
        Select select = method.getAnnotation(Select.class);

        assertThat(select)
                .as("findByKey must NOT carry @Select (would bypass JacksonTypeHandler "
                        + "on Achievement.criteria JSON column — see Bug #8 in implementation report)")
                .isNull();
    }

    @Test
    @DisplayName("Achievement.key field has @TableField(value = \"`key`\") to override auto column list")
    void entityKeyFieldIsBacktickedViaTableField() throws NoSuchFieldException {
        Field field = Achievement.class.getDeclaredField("key");
        TableField tableField = field.getAnnotation(TableField.class);

        assertThat(tableField)
                .as("@TableField must be present on Achievement.key so MyBatis-Plus "
                        + "auto-generated column lists backtick the reserved word (CRITICAL #1)")
                .isNotNull();

        assertThat(tableField.value())
                .as("@TableField.value must backtick the MySQL reserved word")
                .isEqualTo("`key`");
    }

    /**
     * Regression guard for Bug #7 (discovered during implementation, not in
     * the original plan): the previous {@code @Select("SELECT * FROM achievements ...")}
     * on {@code findAllActive} bypassed the
     * {@code @TableField(typeHandler = JacksonTypeHandler.class)} on
     * {@code Achievement.criteria}, so {@code getUserAchievements} always
     * received {@code criteria == null} → {@code progress=0, target=0}.
     * The fix routes through {@code BaseMapper.selectList} which honours
     * the entity-level typeHandler.
     */
    @Test
    @DisplayName("findAllActive does NOT use @Select (must go through BaseMapper for typeHandler)")
    void findAllActiveDoesNotUseSelectAnnotation() throws NoSuchMethodException {
        Method method = AchievementMapper.class.getDeclaredMethod("findAllActive");
        Select select = method.getAnnotation(Select.class);

        assertThat(select)
                .as("findAllActive must NOT carry @Select (would bypass JacksonTypeHandler "
                        + "on Achievement.criteria JSON column — see Bug #7 in implementation report)")
                .isNull();
    }
}
