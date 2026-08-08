package com.ulticode.modules.search.dto;

/**
 * Search index types.
 */
public enum SearchIndexType {

    /**
     * Problems index.
     */
    PROBLEMS("problems"),

    /**
     * Users index.
     */
    USERS("users"),

    /**
     * Forum posts index.
     */
    POSTS("posts"),

    /**
     * Solutions index.
     */
    SOLUTIONS("solutions");

    private final String indexName;

    SearchIndexType(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexName() {
        return indexName;
    }
}
