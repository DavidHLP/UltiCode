package com.ulticode.modules.admin.dto.tag;

import java.util.Set;

/**
 * Single source of truth for tag storage-bucket identifiers and their validation
 * regex. Used by {@code AdminTagController} (via {@code @Pattern}),
 * {@code TagQueryDTO} and {@code UpdateTagDTO} (via {@code @Pattern}), and
 * {@code AdminTagServiceImpl#normalizeType} (via {@link #WHITELIST}) to enforce
 * the same whitelist at every layer.
 *
 * <p>Adding a new bucket (e.g. {@code CONTEST}) requires updating this file
 * only — controller, DTO, and service all pick up the change automatically.</p>
 */
public final class TagTypes {

 /** Canonical uppercased identifiers. Add new buckets here. */
 public static final String PROBLEM = "PROBLEM";
 public static final String FORUM = "FORUM";

 /** Validation regex accepted by {@code @Pattern}. Case-insensitive when applied. */
 public static final String WHITELIST_REGEX = PROBLEM + "|" + FORUM;

 /** Set view used by the service-layer guard {@code AdminTagServiceImpl#normalizeType}. */
 public static final Set<String> WHITELIST = Set.of(PROBLEM, FORUM);

 private TagTypes() {
 // utility class — no instances
 }
}
