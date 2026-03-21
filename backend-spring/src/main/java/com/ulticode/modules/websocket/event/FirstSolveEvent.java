package com.ulticode.modules.websocket.event;

import java.time.Instant;

/**
 * First solve notification event payload.
 *
 * <p>Sent when a user is the first to solve a problem in a contest.
 */
public record FirstSolveEvent(
    String contestId,
    String problemId,
    String problemTitle,
    String userId,
    String username,
    Instant solvedAt) {}
