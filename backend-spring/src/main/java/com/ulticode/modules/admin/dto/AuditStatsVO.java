package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.util.List;

@Data
public class AuditStatsVO {
    private Long totalActions;
    private List<EntityTypeStat> actionsByEntity;
    private List<PerformerStat> topPerformers;
    private List<ActionTypeStat> actionsByType;
}
