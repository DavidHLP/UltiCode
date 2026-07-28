package com.ulticode.common.dbperm;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis Interceptor that enforces Per-Owner DB write boundary rules (P3-DBPERM-001).
 *
 * <p>When a thread executes an SQL mutation (INSERT / UPDATE / DELETE) under an explicit
 * {@link DbOwnerContext}, this interceptor inspects the target table name and logs a
 * structured violation warning if the write crosses owner boundaries.
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class DbOwnerViolationInterceptor implements Interceptor {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
        "(?i)\\b(?:INTO|UPDATE|FROM)\\s+`?([a-zA-Z0-9_]+)`?",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        TableOwner currentOwner = DbOwnerContext.getOwner();
        if (currentOwner != null) {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];
            SqlCommandType commandType = ms.getSqlCommandType();

            if (commandType == SqlCommandType.INSERT || commandType == SqlCommandType.UPDATE || commandType == SqlCommandType.DELETE) {
                BoundSql boundSql = ms.getBoundSql(parameter);
                String sql = boundSql.getSql();
                String targetTable = extractTableName(sql);

                if (targetTable != null) {
                    TableOwner expectedOwner = TableOwnerRegistry.getOwner(targetTable);
                    if (expectedOwner != null && expectedOwner != currentOwner) {
                        log.warn("[DB_OWNER_VIOLATION] Cross-owner DB write attempt detected! Context Owner={}, Target Table '{}' (Owner={}), SqlCommand={}",
                                currentOwner, targetTable, expectedOwner, commandType);
                    }
                }
            }
        }
        return invocation.proceed();
    }

    private String extractTableName(String sql) {
        if (sql == null) {
            return null;
        }
        Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
