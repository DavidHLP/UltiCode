package com.ulticode.app.api.service;

/**
 * Mark-dirty port for flagging a contest's ranking for the next flush tick.
 *
 * <p>Consumed by websocket adapters. Promoted from
 * {@code com.ulticode.modules.contest.port.ContestRankingMarkDirtyPort}
 * during P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
public interface ContestRankingMarkDirtyPort {

    /**
     * Flag a contest's ranking for the next flush tick.
     *
     * @param contestId the contest id (must not be {@code null})
     */
    void markDirty(String contestId);
}
