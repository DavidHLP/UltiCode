package com.ulticode.core;

import com.ulticode.common.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single HTTP readiness surface for the Core profile. */
@RestController
@RequestMapping("/api/v1/core/health")
public final class CoreReadinessController {

    private final CoreReadinessService readiness;

    public CoreReadinessController(CoreReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/ready")
    public ResponseEntity<Result<CoreReadinessService.Snapshot>> ready() {
        CoreReadinessService.Snapshot snapshot = readiness.snapshot();
        if (!snapshot.ready()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.errorWithData(503, "Core dependencies are not ready", snapshot, "t-core"));
        }
        return ResponseEntity.ok(Result.success(snapshot));
    }
}
