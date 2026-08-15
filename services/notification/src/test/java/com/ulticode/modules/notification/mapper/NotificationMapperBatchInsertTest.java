package com.ulticode.modules.notification.mapper;

import org.mockito.Mock;
import com.ulticode.modules.notification.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Compile-time + reflection regression guard for
 * {@link NotificationMapper#batchInsert(List)}.
 *
 * <p>Background: {@code Notification.metadata} is a {@code Map<String,Object>}
 * mapped to a MySQL {@code JSON} column via
 * {@code @TableField(typeHandler=JacksonTypeHandler.class)}. MyBatis does not
 * inherit that metadata inside custom {@code @Insert} SQL fragments, so the
 * {@code #{item.metadata}} parameter must explicitly declare a typeHandler
 * string. Without it, MyBatis throws
 * {@code IllegalStateException: Type handler was null on parameter mapping
 * for property '__frch_item_0.metadata'} at runtime when
 * {@code AdminNotificationServiceImpl.createSystemNotification} is called.
 *
 * <p>This test prevents the regression by asserting the {@code @Insert} SQL
 * body references {@code JacksonTypeHandler}. A full runtime IT
 * (Testcontainers + real MySQL JSON column) is the next level of coverage
 * and should be added alongside this guard.
 */
@DisplayName("NotificationMapper#batchInsert typeHandler guard")
class NotificationMapperBatchInsertTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

 @Test
 @DisplayName("batchInsert declares JacksonTypeHandler for #{item.metadata}")
 void batchInsert_declaresJacksonTypeHandlerForMetadata() throws NoSuchMethodException {
 Method method = NotificationMapper.class.getDeclaredMethod("batchInsert", List.class);
 Insert insert = method.getAnnotation(Insert.class);

 assertThat(insert)
 .as("@Insert annotation must be present on batchInsert")
 .isNotNull();

 String[] value = insert.value();
 assertThat(value)
 .as("@Insert SQL must be present")
 .isNotEmpty();

 String sql = String.join("\n", value);
 assertThat(sql)
 .as("batchInsert SQL must reference JacksonTypeHandler for the metadata column")
 .contains("JacksonTypeHandler");
 assertThat(sql)
 .as("batchInsert SQL must bind typeHandler on the metadata parameter")
 .containsPattern("metadata\\s*,\\s*typeHandler\\s*=\\s*[\\w.$]+JacksonTypeHandler");
 }

 @Test
 @DisplayName("Notification entity retains @TableField JacksonTypeHandler on metadata")
 void entity_metadataStillHasJacksonTypeHandler() throws NoSuchFieldException {
 java.lang.reflect.Field field = Notification.class.getDeclaredField("metadata");
 com.baomidou.mybatisplus.annotation.TableField tableField =
 field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);

 assertThat(tableField)
 .as("@TableField must remain on Notification.metadata to keep BaseMapper CRUD consistent")
 .isNotNull();
 assertThat(tableField.typeHandler())
 .as("@TableField(typeHandler=...) must remain JacksonTypeHandler.class")
 .isEqualTo(com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class);
 }
}
