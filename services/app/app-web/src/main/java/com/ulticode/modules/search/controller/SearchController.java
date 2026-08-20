package com.ulticode.modules.search.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.projection.SearchReadProjection;
import com.ulticode.websecurity.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for search operations.
 * Provides full-text search across problems, users, posts, and solutions.
 */
@Tag(name = "Search", description = "Full-text search API for problems, users, posts, and solutions")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchReadProjection searchProjection;

    @Operation(summary = "Search content",
            description = "Full-text search across problems, users, posts, and solutions. " +
                    "Supports filtering by index type and pagination.")
    @RateLimit(key = "search:read", limit = 60, period = 60)
    @GetMapping
    public Result<SearchResponseVO> search(@Valid SearchQueryDTO queryDTO) {
        return Result.success(searchProjection.search(queryDTO));
    }
}
