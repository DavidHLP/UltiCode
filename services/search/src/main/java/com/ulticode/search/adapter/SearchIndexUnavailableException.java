package com.ulticode.search.adapter;

/** Transport-independent signal for temporary index backend failure. */
public class SearchIndexUnavailableException extends RuntimeException {

    public SearchIndexUnavailableException(Throwable cause) {
        super("Search index backend is unavailable", cause);
    }
}
