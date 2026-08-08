package com.ulticode.auth.adapter.in.web;

import com.ulticode.common.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Process health endpoint for the backend-auth service.
 *
 * <p>This endpoint intentionally has no business dependency so deployment
 * probes can distinguish a running service process from a failed boot.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthHealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("backend-auth shell up");
    }
}
