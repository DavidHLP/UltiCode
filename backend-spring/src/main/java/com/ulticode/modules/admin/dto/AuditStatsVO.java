package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AuditStatsVO {
    private Long totalActions;
    private List<Map<String, Object>> actionsByEntity;
    private List<Map<String, Object>> actionsByPerformer;
    private List<Map<String, Object>> topPerformers;
}