package com.ulticode.modules.search.dto;

/**
 * Search consistency facts made visible at the read seam. These values are
 * part of every response so callers do not have to infer consistency from
 * infrastructure configuration.
 */
public record SearchReadSemantics(
        String mode,
        String source,
        String freshness,
        String ordering,
        String total,
        boolean fallbackApplied) {
}
