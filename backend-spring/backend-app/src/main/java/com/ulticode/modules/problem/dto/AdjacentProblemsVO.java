package com.ulticode.modules.problem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for adjacent problems (prev/next navigation).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjacentProblemsVO {

    /**
     * Previous problem ID (slug) or null if current problem is the first
     */
    private String prev;

    /**
     * Next problem ID (slug) or null if current problem is the last
     */
    private String next;
}
