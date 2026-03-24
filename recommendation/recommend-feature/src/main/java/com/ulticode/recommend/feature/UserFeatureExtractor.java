package com.ulticode.recommend.feature;

import com.ulticode.recommend.feature.model.ProblemInfo;
import com.ulticode.recommend.feature.model.UserFeatures;
import com.ulticode.recommend.feature.model.UserSubmission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracts features from user submissions for recommendation purposes.
 *
 * <p>This class analyzes user submission history to compute various features:
 * <ul>
 *   <li>Activity level and submission patterns</li>
 *   <li>Success rates by difficulty</li>
 *   <li>Tag preferences and mastery levels</li>
 *   <li>Learning velocity and consistency</li>
 * </ul>
 */
public class UserFeatureExtractor {

    private static final int RECENT_DAYS = 7;
    private static final double STRONG_TAG_THRESHOLD = 0.7;
    private static final double WEAK_TAG_THRESHOLD = 0.3;
    private static final int EXPECTED_DAILY_SUBMISSIONS = 3;

    /**
     * Extracts all features from user submissions.
     *
     * @param userId      the user's unique identifier
     * @param submissions list of user submissions
     * @param problems    list of problems with metadata
     * @return extracted user features
     * @throws IllegalArgumentException if userId or submissions is null
     */
    public UserFeatures extractFeatures(String userId, List<UserSubmission> submissions, List<ProblemInfo> problems) {
        validateInput(userId, submissions);

        List<UserSubmission> safeSubmissions = submissions != null ? submissions : Collections.emptyList();
        List<ProblemInfo> safeProblems = problems != null ? problems : Collections.emptyList();

        Map<Long, ProblemInfo> problemMap = buildProblemMap(safeProblems);

        // Calculate activity features
        double activityLevel = calculateActivityLevel(safeSubmissions);
        int totalSubmissions = safeSubmissions.size();
        int recentSubmissions = countRecentSubmissions(safeSubmissions);

        // Calculate skill features
        Map<String, Double> successRates = calculateSuccessRates(safeSubmissions, problemMap);
        String skillLevel = determineSkillLevel(successRates);

        // Calculate tag features
        Map<String, Double> tagPreferences = calculateTagPreferences(safeSubmissions, problemMap);
        Map<String, Double> tagMastery = calculateTagMastery(safeSubmissions, problemMap);
        Map<String, Set<String>> strongWeakTags = identifyStrongWeakTags(tagMastery);

        // Calculate learning features
        double learningVelocity = calculateLearningVelocity(safeSubmissions, problemMap);
        int streakDays = calculateStreakDays(safeSubmissions);
        double consistency = calculateConsistency(safeSubmissions);

        return UserFeatures.builder()
                .userId(userId)
                .activityLevel(activityLevel)
                .totalSubmissions(totalSubmissions)
                .recentSubmissions(recentSubmissions)
                .easySuccessRate(successRates.getOrDefault("Easy", 0.0))
                .mediumSuccessRate(successRates.getOrDefault("Medium", 0.0))
                .hardSuccessRate(successRates.getOrDefault("Hard", 0.0))
                .skillLevel(skillLevel)
                .tagPreferences(tagPreferences)
                .tagMastery(tagMastery)
                .strongTags(strongWeakTags.get("strong"))
                .weakTags(strongWeakTags.get("weak"))
                .learningVelocity(learningVelocity)
                .streakDays(streakDays)
                .consistency(consistency)
                .build();
    }

    /**
     * Calculates activity level based on submission frequency.
     *
     * @param submissions list of user submissions
     * @return activity level normalized to 0-1
     */
    public double calculateActivityLevel(List<UserSubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return 0.0;
        }

        int recentCount = countRecentSubmissions(submissions);
        double expectedSubmissions = EXPECTED_DAILY_SUBMISSIONS * RECENT_DAYS;

        return Math.min(1.0, recentCount / expectedSubmissions);
    }

    /**
     * Calculates success rates by difficulty.
     *
     * @param submissions list of user submissions
     * @param problems    list of problems with metadata
     * @return map of difficulty to success rate
     */
    public Map<String, Double> calculateSuccessRates(List<UserSubmission> submissions, List<ProblemInfo> problems) {
        Map<Long, ProblemInfo> problemMap = buildProblemMap(problems);
        return calculateSuccessRates(submissions, problemMap);
    }

    private Map<String, Double> calculateSuccessRates(List<UserSubmission> submissions, Map<Long, ProblemInfo> problemMap) {
        Map<String, Integer> acceptedByDifficulty = new HashMap<>();
        Map<String, Integer> totalByDifficulty = new HashMap<>();

        // Initialize all difficulties
        acceptedByDifficulty.put("Easy", 0);
        acceptedByDifficulty.put("Medium", 0);
        acceptedByDifficulty.put("Hard", 0);
        totalByDifficulty.put("Easy", 0);
        totalByDifficulty.put("Medium", 0);
        totalByDifficulty.put("Hard", 0);

        for (UserSubmission submission : submissions) {
            ProblemInfo problem = problemMap.get(submission.getProblemId());
            if (problem != null && problem.getDifficulty() != null) {
                String difficulty = problem.getDifficulty();
                totalByDifficulty.merge(difficulty, 1, Integer::sum);
                if (submission.isAccepted()) {
                    acceptedByDifficulty.merge(difficulty, 1, Integer::sum);
                }
            }
        }

        Map<String, Double> successRates = new HashMap<>();
        for (String difficulty : Arrays.asList("Easy", "Medium", "Hard")) {
            int total = totalByDifficulty.get(difficulty);
            int accepted = acceptedByDifficulty.get(difficulty);
            successRates.put(difficulty, total > 0 ? (double) accepted / total : 0.0);
        }

        return successRates;
    }

    /**
     * Calculates tag preferences based on problem distribution.
     *
     * @param submissions list of user submissions
     * @param problems    list of problems with metadata
     * @return map of tag to preference score (0-1)
     */
    public Map<String, Double> calculateTagPreferences(List<UserSubmission> submissions, List<ProblemInfo> problems) {
        Map<Long, ProblemInfo> problemMap = buildProblemMap(problems);
        return calculateTagPreferences(submissions, problemMap);
    }

    private Map<String, Double> calculateTagPreferences(List<UserSubmission> submissions, Map<Long, ProblemInfo> problemMap) {
        if (submissions.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Integer> tagCounts = new HashMap<>();
        Set<Long> uniqueProblems = new HashSet<>();

        for (UserSubmission submission : submissions) {
            uniqueProblems.add(submission.getProblemId());
        }

        int totalProblems = uniqueProblems.size();

        for (Long problemId : uniqueProblems) {
            ProblemInfo problem = problemMap.get(problemId);
            if (problem != null && problem.getTags() != null) {
                for (String tag : problem.getTags()) {
                    tagCounts.merge(tag, 1, Integer::sum);
                }
            }
        }

        Map<String, Double> preferences = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            preferences.put(entry.getKey(), (double) entry.getValue() / totalProblems);
        }

        return preferences;
    }

    /**
     * Calculates tag mastery based on success rates per tag.
     *
     * @param submissions list of user submissions
     * @param problems    list of problems with metadata
     * @return map of tag to mastery level (0-1)
     */
    public Map<String, Double> calculateTagMastery(List<UserSubmission> submissions, List<ProblemInfo> problems) {
        Map<Long, ProblemInfo> problemMap = buildProblemMap(problems);
        return calculateTagMastery(submissions, problemMap);
    }

    private Map<String, Double> calculateTagMastery(List<UserSubmission> submissions, Map<Long, ProblemInfo> problemMap) {
        Map<String, Integer> acceptedByTag = new HashMap<>();
        Map<String, Integer> totalByTag = new HashMap<>();

        for (UserSubmission submission : submissions) {
            ProblemInfo problem = problemMap.get(submission.getProblemId());
            if (problem != null && problem.getTags() != null) {
                for (String tag : problem.getTags()) {
                    totalByTag.merge(tag, 1, Integer::sum);
                    if (submission.isAccepted()) {
                        acceptedByTag.merge(tag, 1, Integer::sum);
                    }
                }
            }
        }

        Map<String, Double> mastery = new HashMap<>();
        for (Map.Entry<String, Integer> entry : totalByTag.entrySet()) {
            String tag = entry.getKey();
            int total = entry.getValue();
            int accepted = acceptedByTag.getOrDefault(tag, 0);
            mastery.put(tag, (double) accepted / total);
        }

        return mastery;
    }

    /**
     * Identifies strong and weak tags based on mastery levels.
     *
     * @param tagMastery map of tag to mastery level
     * @return map containing "strong" and "weak" sets
     */
    public Map<String, Set<String>> identifyStrongWeakTags(Map<String, Double> tagMastery) {
        Set<String> strongTags = new HashSet<>();
        Set<String> weakTags = new HashSet<>();

        if (tagMastery == null) {
            return Map.of("strong", strongTags, "weak", weakTags);
        }

        for (Map.Entry<String, Double> entry : tagMastery.entrySet()) {
            double mastery = entry.getValue();
            if (mastery > STRONG_TAG_THRESHOLD) {
                strongTags.add(entry.getKey());
            } else if (mastery < WEAK_TAG_THRESHOLD) {
                weakTags.add(entry.getKey());
            }
        }

        return Map.of("strong", strongTags, "weak", weakTags);
    }

    /**
     * Determines skill level based on success rates.
     *
     * @param successRates map of difficulty to success rate
     * @return skill level string: "beginner", "intermediate", or "advanced"
     */
    public String determineSkillLevel(Map<String, Double> successRates) {
        if (successRates == null || successRates.isEmpty()) {
            return "beginner";
        }

        double easyRate = successRates.getOrDefault("Easy", 0.0);
        double mediumRate = successRates.getOrDefault("Medium", 0.0);
        double hardRate = successRates.getOrDefault("Hard", 0.0);

        // Advanced: Good at Medium and some Hard success
        if (mediumRate >= 0.7 && hardRate >= 0.3) {
            return "advanced";
        }

        // Intermediate: Good at Easy and some Medium success
        if (easyRate >= 0.6 && mediumRate >= 0.4) {
            return "intermediate";
        }

        return "beginner";
    }

    // ==================== Private Helper Methods ====================

    private void validateInput(String userId, List<UserSubmission> submissions) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (submissions == null) {
            throw new IllegalArgumentException("submissions cannot be null");
        }
    }

    private Map<Long, ProblemInfo> buildProblemMap(List<ProblemInfo> problems) {
        if (problems == null) {
            return Collections.emptyMap();
        }
        return problems.stream()
                .filter(p -> p != null && p.getProblemId() != null)
                .collect(Collectors.toMap(ProblemInfo::getProblemId, p -> p, (a, b) -> a));
    }

    private int countRecentSubmissions(List<UserSubmission> submissions) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RECENT_DAYS);
        return (int) submissions.stream()
                .filter(s -> s.getTimestamp() != null && s.getTimestamp().isAfter(cutoff))
                .count();
    }

    private double calculateLearningVelocity(List<UserSubmission> submissions, Map<Long, ProblemInfo> problemMap) {
        if (submissions.size() < 2) {
            return 0.0;
        }

        // Sort submissions by timestamp
        List<UserSubmission> sorted = submissions.stream()
                .filter(s -> s.getTimestamp() != null)
                .sorted(Comparator.comparing(UserSubmission::getTimestamp))
                .collect(Collectors.toList());

        if (sorted.size() < 2) {
            return 0.0;
        }

        // Calculate improvement rate by comparing first half vs second half success rates
        int mid = sorted.size() / 2;
        List<UserSubmission> firstHalf = sorted.subList(0, mid);
        List<UserSubmission> secondHalf = sorted.subList(mid, sorted.size());

        double firstHalfRate = calculateOverallSuccessRate(firstHalf);
        double secondHalfRate = calculateOverallSuccessRate(secondHalf);

        // Velocity is the improvement in success rate
        return Math.max(0, secondHalfRate - firstHalfRate);
    }

    private double calculateOverallSuccessRate(List<UserSubmission> submissions) {
        if (submissions.isEmpty()) {
            return 0.0;
        }
        long accepted = submissions.stream().filter(UserSubmission::isAccepted).count();
        return (double) accepted / submissions.size();
    }

    private int calculateStreakDays(List<UserSubmission> submissions) {
        if (submissions.isEmpty()) {
            return 0;
        }

        // Get unique submission dates
        Set<LocalDate> submissionDates = submissions.stream()
                .filter(s -> s.getTimestamp() != null)
                .map(s -> s.getTimestamp().toLocalDate())
                .collect(Collectors.toSet());

        if (submissionDates.isEmpty()) {
            return 0;
        }

        // Count consecutive days from today
        int streak = 0;
        LocalDate currentDate = LocalDateTime.now().toLocalDate();

        while (submissionDates.contains(currentDate)) {
            streak++;
            currentDate = currentDate.minusDays(1);
        }

        return streak;
    }

    private double calculateConsistency(List<UserSubmission> submissions) {
        if (submissions.size() < 2) {
            return submissions.isEmpty() ? 0.0 : 1.0;
        }

        // Count submissions per day
        Map<LocalDate, Integer> submissionsPerDay = submissions.stream()
                .filter(s -> s.getTimestamp() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getTimestamp().toLocalDate(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        if (submissionsPerDay.size() < 2) {
            return 1.0; // Single day = consistent
        }

        // Calculate coefficient of variation (lower = more consistent)
        double mean = submissionsPerDay.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        if (mean == 0) {
            return 0.0;
        }

        double variance = submissionsPerDay.values().stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);
        double cv = stdDev / mean;

        // Convert CV to consistency score (CV of 0 = 1.0, higher CV = lower consistency)
        return Math.max(0, 1.0 - cv / 2.0);
    }
}
