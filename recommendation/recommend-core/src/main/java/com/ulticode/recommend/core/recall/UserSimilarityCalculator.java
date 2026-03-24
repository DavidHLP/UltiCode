package com.ulticode.recommend.core.recall;

import java.util.HashSet;
import java.util.Set;

/**
 * Calculates similarity between users based on their problem-solving history.
 *
 * <p>This calculator uses cosine similarity to measure how similar two users are
 * based on the overlap of problems they have solved.
 *
 * <p>Similarity formula:
 * <pre>
 * similarity(u1, u2) = |P(u1) ∩ P(u2)| / sqrt(|P(u1)| * |P(u2)|)
 * </pre>
 * where P(u) is the set of problems solved by user u.
 */
public class UserSimilarityCalculator {

    /**
     * Calculates the cosine similarity between two users based on their solved problems.
     *
     * <p>The similarity is computed as the size of the intersection divided by
     * the geometric mean of the set sizes. This produces a value between 0 and 1:
     * <ul>
     *   <li>1.0 - identical problem sets</li>
     *   <li>0.0 - no common problems</li>
     *   <li>Values between 0 and 1 indicate partial overlap</li>
     * </ul>
     *
     * @param user1Problems the set of problem IDs solved by user 1
     * @param user2Problems the set of problem IDs solved by user 2
     * @return the similarity score between 0 and 1, or 0.0 if either set is null or empty
     */
    public double calculateSimilarity(Set<Long> user1Problems, Set<Long> user2Problems) {
        if (user1Problems == null || user2Problems == null ||
            user1Problems.isEmpty() || user2Problems.isEmpty()) {
            return 0.0;
        }

        // Calculate intersection size without mutating original sets
        int intersectionSize = 0;
        for (Long problemId : user1Problems) {
            if (user2Problems.contains(problemId)) {
                intersectionSize++;
            }
        }

        // Calculate cosine similarity
        double denominator = Math.sqrt(user1Problems.size() * user2Problems.size());

        return intersectionSize / denominator;
    }
}
