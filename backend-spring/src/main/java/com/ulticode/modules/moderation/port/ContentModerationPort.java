package com.ulticode.modules.moderation.port;

/**
 * Content-flagging and author-resolution seam that isolates the
 * moderation state machine from the five content-provider mappers
 * (forum post, forum comment, solution, solution comment, problem).
 *
 * <p>Before this port, {@link DefaultModerationWritePort} injected all
 * five content mappers directly and inlined a switch-on-entity-type
 * in both {@code resolveAuthorId} and {@code updateContentFlagStatus}.
 * This port lifts those two operations behind a single seam so the
 * state machine depends on one interface, not five mapper types.
 *
 * <p>The adapter ({@link DefaultContentModerationAdapter}) still
 * dispatches by entity type internally; a future tightening can move
 * each entity type's logic into its provider module's own adapter.
 *
 * @author ulticode
 */
public interface ContentModerationPort {

    String resolveAuthorId(String entityType, String entityId);

    void updateFlagStatus(String entityType, String entityId, boolean isFlagged, String reason);
}
