package com.ulticode.recommend.core.rerank;

import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Re-ranking strategy that ensures tag diversity in recommendations.
 *
 * <p>This strategy groups items by their primary tag (first tag in the set)
 * and uses round-robin selection to pick items from each tag group,
 * ensuring balanced tag distribution in the final results.
 */
public class DiversityReRankStrategy implements ReRankStrategy {

    private static final String NO_TAG_KEY = "__NO_TAG__";
    private static final int DEFAULT_PRIORITY = 50;

    @Override
    public List<RecommendItem> rerank(
            List<RecommendItem> items,
            RecommendContext context,
            UserProfile profile
    ) {
        // Handle null or empty input
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        // Get the requested size from context
        int requestedSize = context != null ? context.getSize() : 10;
        if (requestedSize <= 0) {
            return Collections.emptyList();
        }

        // Group items by primary tag
        Map<String, LinkedList<RecommendItem>> itemsByTag = groupByPrimaryTag(items);

        // Select items using round-robin from each group
        return selectItemsRoundRobin(itemsByTag, requestedSize);
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * Groups items by their primary tag (first tag in the set).
     *
     * <p>Items without tags are grouped under a special key.
     * Items within each group are sorted by score in descending order.
     *
     * @param items the items to group
     * @return map of primary tag to list of items
     */
    private Map<String, LinkedList<RecommendItem>> groupByPrimaryTag(List<RecommendItem> items) {
        Map<String, LinkedList<RecommendItem>> itemsByTag = new LinkedHashMap<>();

        for (RecommendItem item : items) {
            String primaryTag = getPrimaryTag(item);

            itemsByTag.computeIfAbsent(primaryTag, k -> new LinkedList<>())
                    .add(item);
        }

        // Sort each group by score (descending)
        for (LinkedList<RecommendItem> group : itemsByTag.values()) {
            group.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        }

        return itemsByTag;
    }

    /**
     * Gets the primary tag from an item (first tag in the set).
     *
     * @param item the item to get the primary tag from
     * @return the primary tag, or a special key for items without tags
     */
    private String getPrimaryTag(RecommendItem item) {
        Set<String> tags = item.getTags();
        if (tags == null || tags.isEmpty()) {
            return NO_TAG_KEY;
        }
        return tags.iterator().next();
    }

    /**
     * Selects items using round-robin from each tag group.
     *
     * <p>This ensures diversity by taking one item from each tag group
     * in rotation until the requested size is reached.
     *
     * @param itemsByTag   map of tag to items
     * @param requestedSize maximum number of items to return
     * @return list of selected items with diverse tags
     */
    private List<RecommendItem> selectItemsRoundRobin(
            Map<String, LinkedList<RecommendItem>> itemsByTag,
            int requestedSize
    ) {
        List<RecommendItem> result = new ArrayList<>();

        // Continue round-robin until we have enough items or run out
        while (result.size() < requestedSize && !itemsByTag.isEmpty()) {
            // Remove empty groups
            itemsByTag.values().removeIf(LinkedList::isEmpty);

            if (itemsByTag.isEmpty()) {
                break;
            }

            // Take one item from each non-empty group in order
            for (LinkedList<RecommendItem> group : itemsByTag.values()) {
                if (result.size() >= requestedSize) {
                    break;
                }
                if (!group.isEmpty()) {
                    result.add(group.removeFirst());
                }
            }
        }

        return result;
    }
}
