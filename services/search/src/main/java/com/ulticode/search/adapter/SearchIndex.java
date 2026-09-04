package com.ulticode.search.adapter;

/** Small Search Index seam; Meili SDK types stay inside its Adapter. */
public interface SearchIndex {
    void health();

    IndexWriter index(String name);

    interface IndexWriter {
        void upsert(String document);

        void delete(String documentId);
    }
}
