package com.ulticode.common.dbperm;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DbOwnerViolationInterceptorTest {

    @Mock
    private Invocation invocation;

    @Mock
    private MappedStatement mappedStatement;

    @Mock
    private BoundSql boundSql;

    private DbOwnerViolationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new DbOwnerViolationInterceptor();
        when(invocation.getArgs()).thenReturn(new Object[]{mappedStatement, new Object()});
    }

    @AfterEach
    void tearDown() {
        DbOwnerContext.clear();
    }

    @Test
    @DisplayName("TableOwnerRegistry maps core tables to correct owners")
    void tableOwnerRegistry_correctMapping() {
        assertThat(TableOwnerRegistry.getOwner("users")).isEqualTo(TableOwner.AUTH);
        assertThat(TableOwnerRegistry.getOwner("refresh_tokens")).isEqualTo(TableOwner.AUTH);
        assertThat(TableOwnerRegistry.getOwner("audit_logs")).isEqualTo(TableOwner.ADMIN);
        // audit_outbox is owner-neutral: shared integration seam written by every domain (P3-AUDIT-001)
        assertThat(TableOwnerRegistry.getOwner("audit_outbox")).isNull();
        assertThat(TableOwnerRegistry.getOwner("problems")).isEqualTo(TableOwner.APP);
        assertThat(TableOwnerRegistry.getOwner("contests")).isEqualTo(TableOwner.APP);
    }

    @Test
    @DisplayName("intercept allows intra-owner write without warning")
    void intercept_sameOwner_proceeds() throws Throwable {
        DbOwnerContext.setOwner(TableOwner.AUTH);
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(mappedStatement.getBoundSql(any())).thenReturn(boundSql);
        when(boundSql.getSql()).thenReturn("UPDATE users SET is_active = 1 WHERE id = '1'");
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(1);
        verify(invocation).proceed();
    }

    @Test
    @DisplayName("intercept detects cross-owner write attempt and proceeds")
    void intercept_crossOwner_detectsViolation() throws Throwable {
        DbOwnerContext.setOwner(TableOwner.AUTH); // Auth owner context
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        when(mappedStatement.getBoundSql(any())).thenReturn(boundSql);
        // Attempting to write directly to problems table (owned by APP)
        when(boundSql.getSql()).thenReturn("UPDATE `problems` SET title = 'Hacked' WHERE id = 'p1'");
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo(1);
        verify(invocation).proceed();
    }
}
