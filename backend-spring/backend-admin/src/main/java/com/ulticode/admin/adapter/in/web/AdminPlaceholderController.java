package com.ulticode.admin.adapter.in.web;

import com.ulticode.common.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1-INFRA-005 placeholder controller for the admin service shell.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminPlaceholderController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("backend-admin shell up");
    }
}
