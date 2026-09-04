package com.ulticode.search.adapter;

import com.meilisearch.sdk.Client;
import lombok.RequiredArgsConstructor;

/** Local and hosted Meili endpoints share this adapter contract. */
@RequiredArgsConstructor
public class MeiliSearchIndexAdapter implements SearchIndex {

    private final Client client;
    @Override
    public void health() {
        try {
            client.health();
        } catch (com.meilisearch.sdk.exceptions.MeilisearchException exception) {
            throw new SearchIndexUnavailableException(exception);
        }
    }


    @Override
    public IndexWriter index(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("index name is required");
        }
        com.meilisearch.sdk.Index index = client.index(name);
        return new IndexWriter() {
            @Override
            public void upsert(String document) {
                try {
                    index.addDocuments(document);
                } catch (com.meilisearch.sdk.exceptions.MeilisearchException exception) {
                    throw new SearchIndexUnavailableException(exception);
                }
            }

            @Override
            public void delete(String documentId) {
                try {
                    index.deleteDocument(documentId);
                } catch (com.meilisearch.sdk.exceptions.MeilisearchException exception) {
                    throw new SearchIndexUnavailableException(exception);
                }
            }
        };
    }
}
