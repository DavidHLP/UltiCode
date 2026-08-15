package com.ulticode.modules.notification.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL guard for the Admin announcement read path.
 *
 * <p>Only rows carrying an {@code announcement_id} are broadcast copies.
 * Personal notifications may share the same category but must never appear
 * in the deduplicated Admin system-announcement list.
 */
@DisplayName("NotificationMapper announcement read SQL guard")
class NotificationMapperAnnouncementReadSqlGuardTest {

    @Test
    @DisplayName("deduplicated announcement query excludes rows without announcement ids")
    void deduplicatedAnnouncementQueryRequiresAnnouncementId() throws NoSuchMethodException {
        Method method = NotificationMapper.class.getDeclaredMethod(
                "selectDedupedAnnouncements",
                Page.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);
        Select select = method.getAnnotation(Select.class);

        assertThat(select).as("announcement read query must use @Select").isNotNull();
        String sql = String.join("\n", select.value());
        assertThat(sql)
                .as("personal notifications must not enter the Admin announcement read path")
                .contains("announcement_id IS NOT NULL");
        assertThat(sql).contains("GROUP BY announcement_id");
    }

    @Test
    @DisplayName("single-row mutations require an announcement group")
    void singleRowMutationsRequireAnnouncementId() throws NoSuchMethodException {
        Method deleteMethod = NotificationMapper.class.getDeclaredMethod(
                "softDeleteAnnouncement", String.class, String.class);
        Method updateMethod = NotificationMapper.class.getDeclaredMethod(
                "updateAnnouncement",
                String.class, String.class, String.class, String.class,
                String.class, String.class, String.class);

        String deleteSql = String.join("\n",
                deleteMethod.getAnnotation(Update.class).value());
        String updateSql = String.join("\n",
                updateMethod.getAnnotation(Update.class).value());

        assertThat(deleteSql).contains("announcement_id IS NOT NULL");
        assertThat(updateSql).contains("announcement_id IS NOT NULL");
    }
}
