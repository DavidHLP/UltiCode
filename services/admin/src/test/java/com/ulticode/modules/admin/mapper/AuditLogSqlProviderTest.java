package com.ulticode.modules.admin.mapper;

import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.projection.AuditLogQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogSqlProviderTest {

    private final AuditLogSqlProvider provider = new AuditLogSqlProvider();

    @Test
    void everyAggregateUsesTheSameTypedFilterSetIncludingEntityId() {
        AuditLogQueryDTO dto = new AuditLogQueryDTO();
        dto.setPerformerId("performer-1");
        dto.setUserId("user-1");
        dto.setEntityType("PROBLEM");
        dto.setEntityId("problem-1");
        dto.setAction("UPDATE");
        dto.setSearch("problem");
        dto.setStartDate(LocalDateTime.of(2026, 9, 1, 0, 0));
        dto.setEndDate(LocalDateTime.of(2026, 10, 1, 0, 0));
        Map<String, Object> parameters = Map.of("query", AuditLogQuery.from(dto));

        String[] statements = {
                provider.selectStatsByEntityType(parameters),
                provider.selectStatsByPerformer(parameters),
                provider.selectStatsByActionType(parameters)
        };

        for (String statement : statements) {
            assertThat(statement)
                    .contains("created_at >= #{query.startDate}")
                    .contains("created_at < #{query.endDate}")
                    .contains("performer_id = #{query.performerId}")
                    .contains("user_id = #{query.userId}")
                    .contains("entity_type = #{query.entityType}")
                    .contains("entity_id = #{query.entityId}")
                    .contains("action = #{query.action}")
                    .contains("#{query.search}");
        }
    }

    @Test
    void blankSearchIsIgnoredAcrossAggregateStatements() {
        AuditLogQueryDTO dto = new AuditLogQueryDTO();
        dto.setSearch("  ");
        Map<String, Object> parameters = Map.of("query", AuditLogQuery.from(dto));

        assertThat(provider.selectStatsByEntityType(parameters))
                .doesNotContain("query.search");
    }
}
