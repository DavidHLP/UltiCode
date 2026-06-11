package com.ulticode.modules.problem.vo;

import com.ulticode.modules.problem.entity.ProblemNote;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * VO for problem note response.
 * Used by GET/POST /problems/{problemId}/note.
 *
 * @author Claude
 * @since 2026-06-11
 */
@Data
public class ProblemNoteVO {

    /**
     * Note content
     */
    private String content;

    /**
     * Last update timestamp
     */
    private LocalDateTime updateTime;

    /**
     * Build a VO from the given entity.
     *
     * @param entity the source entity
     * @return the populated VO
     */
    public static ProblemNoteVO from(ProblemNote entity) {
        ProblemNoteVO vo = new ProblemNoteVO();
        vo.setContent(entity.getContent());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
