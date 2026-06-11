package com.ulticode.modules.solution.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.solution.dto.SolutionTopicVO;
import com.ulticode.modules.solution.service.SolutionTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Solution topic endpoints (read-only).
 * Powers console/src/views/post-editor/solutions/SolutionsEditView.vue
 * topic picker via /solution-topics.
 */
@Tag(name = "Solution Topics", description = "Solution topic taxonomy endpoints")
@RestController
@RequiredArgsConstructor
public class SolutionTopicController {

    private final SolutionTopicService solutionTopicService;

    @Operation(
            summary = "List solution topics",
            description = "Returns all active solution topics ordered by sort_order, " +
                    "with current solution counts. Used by SolutionEditView topic picker."
    )
    @GetMapping("/solution-topics")
    public Result<List<SolutionTopicVO>> listTopics() {
        return Result.success(solutionTopicService.listTopics());
    }
}
