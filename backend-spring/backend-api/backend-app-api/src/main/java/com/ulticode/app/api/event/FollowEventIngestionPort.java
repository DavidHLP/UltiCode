package com.ulticode.app.api.event;

/**
 * Dubbo RPC contract for ingesting follow domain events cross-process.
 */
public interface FollowEventIngestionPort {
    /**
     * Ingest and dispatch a follow/unfollow domain event.
     */
    void ingestFollowEvent(FollowDomainEvent event);
}
