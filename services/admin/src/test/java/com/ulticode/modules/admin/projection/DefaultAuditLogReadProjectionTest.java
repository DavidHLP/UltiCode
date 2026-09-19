package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.AuditStatsVO;
import com.ulticode.modules.admin.entity.AuditLog;
import com.ulticode.modules.admin.mapper.AuditLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuditLogReadProjectionTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private AdminUserEnricher userEnricher;

    private DefaultAuditLogReadProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAuditLogReadProjection(auditLogMapper, userEnricher);
    }

    @Test
    void statsUseOneTypedQueryForAllAggregatesAndMapRowsWithoutRawMaps() {
        AuditLogQueryDTO dto = query();
        AuditLogQuery query = AuditLogQuery.from(dto);

        when(auditLogMapper.selectCount(any())).thenReturn(7L);
        when(auditLogMapper.selectStatsByEntityType(any()))
                .thenReturn(List.of(new AuditLogMapper.EntityTypeCount("PROBLEM", 4L)));
        when(auditLogMapper.selectStatsByPerformer(any()))
                .thenReturn(List.of(new AuditLogMapper.PerformerCount("performer-1", 3L)));
        when(auditLogMapper.selectStatsByActionType(any()))
                .thenReturn(List.of(new AuditLogMapper.ActionTypeCount("UPDATE", 7L)));
        when(userEnricher.enrich(Set.of("performer-1")))
                .thenReturn(Map.of("performer-1",
                        new AdminUserSummary("performer-1", "admin", "ADMIN", "Admin", null, null)));

        AuditStatsVO stats = projection.findStats(query);

        assertThat(stats.getTotalActions()).isEqualTo(7L);
        assertThat(stats.getActionsByEntity()).extracting("entityType", "count")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PROBLEM", 4L));
        assertThat(stats.getTopPerformers()).extracting("performerId", "username", "count")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("performer-1", "admin", 3L));
        assertThat(stats.getActionsByType()).extracting("actionType", "count")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("UPDATE", 7L));

        ArgumentCaptor<AuditLogQuery> queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(auditLogMapper).selectStatsByEntityType(queryCaptor.capture());
        AuditLogQuery captured = queryCaptor.getValue();
        verify(auditLogMapper).selectStatsByPerformer(captured);
        verify(auditLogMapper).selectStatsByActionType(captured);
        assertThat(captured.getEntityId()).isEqualTo("problem-1");
        assertThat(captured.getSearch()).isEqualTo("UPDATE");
    }

    @Test
    void pageReadKeepsProjectionAndUserEnrichmentInTheReadModule() {
        AuditLog log = new AuditLog();
        log.setId("audit-1");
        log.setPerformerId("performer-1");
        log.setAction("UPDATE");
        log.setEntityType("PROBLEM");
        log.setEntityId("problem-1");
        log.setCreatedAt(LocalDateTime.of(2026, 9, 19, 10, 0));

        Page<AuditLog> result = new Page<>(1, 50);
        result.setRecords(List.of(log));
        result.setTotal(1);
        when(auditLogMapper.selectPage(any(), any())).thenReturn(result);
        when(userEnricher.enrich(Set.of("performer-1")))
                .thenReturn(Map.of("performer-1",
                        new AdminUserSummary("performer-1", "admin", "ADMIN", "Admin", null, null)));

        var page = projection.findPage(AuditLogQuery.from(new AuditLogQueryDTO()));

        assertThat(page.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo("audit-1");
            assertThat(item.getPerformer().getUsername()).isEqualTo("admin");
        });
        assertThat(page.getTotal()).isEqualTo(1L);
    }

    @Test
    void exportUsesTheSameProjectionAndKeepsRowsWhenUserEnrichmentIsPartial() {
        AuditLog log = auditLog("audit-export-1", "performer-1", "user-1");
        when(auditLogMapper.selectList(any())).thenReturn(List.of(log));
        when(userEnricher.enrich(Set.of("performer-1", "user-1")))
                .thenReturn(Map.of("performer-1",
                        new AdminUserSummary("performer-1", "admin", "ADMIN", "Admin", null, null)));

        List<AuditLogVO> exported = projection.findForExport(
                AuditLogQuery.from(new AuditLogQueryDTO()), 100);

        assertThat(exported).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo("audit-export-1");
            assertThat(item.getPerformer().getUsername()).isEqualTo("admin");
            assertThat(item.getUser()).isNull();
        });
        verify(auditLogMapper).selectList(any());
    }

    private AuditLog auditLog(String id, String performerId, String userId) {
        AuditLog log = new AuditLog();
        log.setId(id);
        log.setPerformerId(performerId);
        log.setUserId(userId);
        log.setAction("UPDATE");
        log.setEntityType("PROBLEM");
        log.setEntityId("problem-1");
        log.setCreatedAt(LocalDateTime.of(2026, 9, 19, 10, 0));
        return log;
    }

    private AuditLogQueryDTO query() {
        AuditLogQueryDTO query = new AuditLogQueryDTO();
        query.setPerformerId("performer-1");
        query.setEntityId("problem-1");
        query.setSearch("UPDATE");
        query.setStartDate(LocalDateTime.of(2026, 9, 1, 0, 0));
        query.setEndDate(LocalDateTime.of(2026, 10, 1, 0, 0));
        return query;
    }
}
