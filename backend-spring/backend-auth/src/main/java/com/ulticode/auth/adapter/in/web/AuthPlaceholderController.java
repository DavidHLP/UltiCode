package com.ulticode.auth.adapter.in.web;

import com.ulticode.common.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1-INFRA-005 placeholder controller for the auth service shell.
 *
 * <p>Returns a tiny health signal that lets the smoke harness assert that
 * the service is up and reachable without depending on any business port.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthPlaceholderController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("backend-auth shell up");
    }
}
