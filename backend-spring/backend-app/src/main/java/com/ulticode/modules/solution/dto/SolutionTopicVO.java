package com.ulticode.modules.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VO matching frontend console/src/types/topic.ts::SolutionTopic interface.
 * Contract: { id: string; name: string; count: number }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolutionTopicVO {
    private String id;
    private String name;
    private Integer count;
}
