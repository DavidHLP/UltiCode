package com.ulticode.modules.forum.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ForumCommentMapperSQLGuardTest {

    @Test
    void moderationMutationsExcludeAlreadyDeletedComments() throws NoSuchMethodException {
        Method flagMethod = ForumCommentMapper.class.getDeclaredMethod(
                "updateFlagStatus", String.class, boolean.class, String.class);
        Method deleteMethod = ForumCommentMapper.class.getDeclaredMethod(
                "softDelete", String.class, String.class);

        String flagSql = String.join(" ", flagMethod.getAnnotation(Update.class).value())
                .toLowerCase(Locale.ROOT);
        String deleteSql = String.join(" ", deleteMethod.getAnnotation(Update.class).value())
                .toLowerCase(Locale.ROOT);

        assertThat(flagSql).contains("where id = #{id} and is_deleted = 0");
        assertThat(deleteSql).contains("where id = #{commentid} and is_deleted = 0");
    }
}
