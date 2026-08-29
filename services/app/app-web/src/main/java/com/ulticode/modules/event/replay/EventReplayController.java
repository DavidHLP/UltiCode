package com.ulticode.modules.event.replay;

import com.ulticode.common.response.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ulticode.modules.event.outbox.IntegrationOutboxRecord;
import com.ulticode.modules.event.inbox.ConsumerInboxRecord;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoint for event replay and DLQ management (P6-REPLAY-001).
 *
 * <p>Every operation is protected at the method boundary because the App
 * security filter intentionally permits the HTTP shell to method security.
 */
@Slf4j
@RestController
@RequestMapping("/admin/event-replay")
@RequiredArgsConstructor
public class EventReplayController {

    private final EventReplayService replayService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/outbox/replay")
    public Result<Map<String, Integer>> replayOutbox(
            @RequestParam(required = false) String aggregateId) {
        int count = replayService.replayOutbox(aggregateId);
        return Result.success(Map.of("replayed", count));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/inbox/replay")
    public Result<Map<String, Integer>> replayInbox(
            @RequestParam String consumer,
            @RequestParam(required = false) String eventId) {
        int count = replayService.replayInbox(consumer, eventId);
        return Result.success(Map.of("replayed", count));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/outbox/dlq")
    public Result<List<IntegrationOutboxRecord>> listDeadOutbox() {
        return Result.success(replayService.listDeadOutbox());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/inbox/dlq")
    public Result<List<ConsumerInboxRecord>> listDeadInbox(@RequestParam String consumer) {
        return Result.success(replayService.listDeadInbox(consumer));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/outbox/dlq")
    public Result<Map<String, Integer>> clearDeadOutbox() {
        int count = replayService.clearDeadOutbox();
        return Result.success(Map.of("deleted", count));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/outbox/dlq/reroute")
    public Result<Map<String, Integer>> rerouteDeadOutbox() {
        int count = replayService.rerouteDeadOutbox();
        return Result.success(Map.of("rerouted", count));
    }
}
