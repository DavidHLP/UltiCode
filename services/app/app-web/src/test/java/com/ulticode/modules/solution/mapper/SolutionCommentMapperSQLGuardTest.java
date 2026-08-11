package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.solution.entity.SolutionComment;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SolutionCommentMapperSQLGuardTest {

    @Test
    void updatedAtSortUsesTheSchemaColumn() throws NoSuchMethodException {
        Method method = SolutionCommentMapper.class.getDeclaredMethod(
                "selectPageIgnoreDeleted",
                Page.class,
                Boolean.class,
                Boolean.class,
                String.class,
                String.class,
                String.class,
                String.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).toLowerCase(Locale.ROOT);

        assertThat(sql).contains("sortby == 'updatedat'");
        assertThat(sql).contains("updated_at");
        assertThat(sql).doesNotContain("edited_at");
    }
}
