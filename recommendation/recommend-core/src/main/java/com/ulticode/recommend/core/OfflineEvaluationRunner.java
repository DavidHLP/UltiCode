package com.ulticode.recommend.core;

import com.ulticode.recommend.core.evaluator.EvaluationInput;
import com.ulticode.recommend.core.evaluator.OfflineEvaluator;
import com.ulticode.recommend.core.evaluator.OfflineMetrics;
import com.ulticode.recommend.core.model.RecommendContext;
import com.ulticode.recommend.core.model.RecommendItem;
import com.ulticode.recommend.core.model.UserProfile;
import com.ulticode.recommend.core.rank.RankStrategy;
import com.ulticode.recommend.core.rank.RuleRankStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ulticode.recommend.core.recall.CFRecallStrategy;
import com.ulticode.recommend.core.recall.ColdStartStrategy;
import com.ulticode.recommend.core.recall.ContentRecallStrategy;
import com.ulticode.recommend.core.recall.HotRecallStrategy;
import com.ulticode.recommend.core.recall.RecallStrategy;
import com.ulticode.recommend.core.rerank.DiversityReRankStrategy;
import com.ulticode.recommend.core.rerank.FreshnessReRankStrategy;
import com.ulticode.recommend.core.rerank.ReRankStrategy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Offline Evaluation Runner for the recommendation system.
 *
 * <h2>Overview</h2>
 * <p>Runs offline evaluation by loading data from MySQL, building user profiles,
 * generating recommendations, and evaluating with OfflineEvaluator.
 *
 * <h2>Usage</h2>
 * <pre>
 * # Build the project
 * cd recommendation && mvn clean package -DskipTests
 *
 * # Run evaluation
 * java -cp "recommend-core/target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
 *      -Ddb.url="jdbc:mysql://localhost:23306/ulticode" \
 *      -Ddb.user="<your-db-user>" \
 *      -Ddb.password="<your-db-password>" \
 *      com.ulticode.recommend.core.OfflineEvaluationRunner
 * </pre>
 */
public class OfflineEvaluationRunner {
    private static final Logger log = LoggerFactory.getLogger(OfflineEvaluationRunner.class);

    private static final int DEFAULT_K = 10;
    private static final double STRONG_TAG_THRESHOLD = 0.7;
    private static final double WEAK_TAG_THRESHOLD = 0.3;

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int k;

    private final OfflineEvaluator evaluator = new OfflineEvaluator();
    private List<RecommendItem> allProblems = new ArrayList<>();
    private Map<String, Set<Long>> userProblemMatrix = new HashMap<>();

    public static void main(String[] args) {
        String dbUrl = System.getProperty("db.url",
                System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:23306/ulticode"));
        String dbUser = System.getProperty("db.user",
                System.getenv().getOrDefault("DB_USER", ""));
        String dbPassword = System.getProperty("db.password",
                System.getenv().getOrDefault("DB_PASSWORD", ""));
        int k = Integer.parseInt(System.getProperty("k", String.valueOf(DEFAULT_K)));

        new OfflineEvaluationRunner(dbUrl, dbUser, dbPassword, k).run();
    }

    public OfflineEvaluationRunner(String dbUrl, String dbUser, String dbPassword, int k) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.k = k;
    }

    public void run() {
        printHeader();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("ERROR: MySQL JDBC Driver not found!");
            System.exit(1);
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            // Load data
            System.out.println("Loading data from database...");
            loadData(conn);
            System.out.println("Loaded " + allProblems.size() + " problems");
            System.out.println("Loaded " + userProblemMatrix.size() + " users");
            System.out.println();

            if (allProblems.isEmpty()) {
                System.out.println("No problems found. Seed database first:");
                System.out.println("  cd backend && USE_NEW_SEED=true pnpm db:reset");
                return;
            }

            // Load user data
            Map<String, UserData> userDataMap = loadUserData(conn);

            // Create engine
            RecommendEngine engine = createEngine();

            // Evaluate users
            List<EvaluationResult> results = new ArrayList<>();
            for (Map.Entry<String, UserData> entry : userDataMap.entrySet()) {
                if (entry.getValue().attempts < 3) continue;

                UserProfile profile = buildProfile(entry.getKey(), entry.getValue());
                EvaluationResult result = evaluate(engine, profile, entry.getValue());
                results.add(result);
            }

            if (results.isEmpty()) {
                System.out.println("No users with enough submissions (min 3).");
                return;
            }

            // Print results
            printResults(results);

        } catch (SQLException e) {
            log.error("Database error", e);
        }
    }

    private void printHeader() {
        System.out.println("============================================================");
        System.out.println("Offline Recommendation Evaluation");
        System.out.println("============================================================");
        System.out.println("K: " + k);
        System.out.println("Database: " + dbUrl);
        System.out.println();
    }

    private void loadData(Connection conn) throws SQLException {
        // Load problems
        String sql = "SELECT id, slug, title, difficulty, acceptance_rate FROM problems " +
                     "WHERE is_deleted = false AND is_published = true";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                Set<String> tags = loadTags(conn, id);
                allProblems.add(RecommendItem.builder()
                        .problemId(id)
                        .slug(rs.getString("slug"))
                        .title(rs.getString("title"))
                        .difficulty(rs.getString("difficulty"))
                        .tags(tags)
                        .qualityScore(rs.getDouble("acceptance_rate") / 100.0)
                        .score(rs.getDouble("acceptance_rate") / 100.0)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        // Load user-problem matrix
        sql = "SELECT DISTINCT user_id, problem_id FROM submissions WHERE status = 'Accepted'";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                userProblemMatrix
                    .computeIfAbsent(rs.getString("user_id"), k -> new HashSet<>())
                    .add(rs.getLong("problem_id"));
            }
        }
    }

    private Set<String> loadTags(Connection conn, long problemId) throws SQLException {
        Set<String> tags = new HashSet<>();
        String sql = "SELECT pt.label FROM problem_tag_relations ptr " +
                     "JOIN problem_tags pt ON ptr.tag_id = pt.id WHERE ptr.problem_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, problemId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) tags.add(rs.getString("label"));
            }
        }
        return tags;
    }

    private Map<String, UserData> loadUserData(Connection conn) throws SQLException {
        Map<String, UserData> map = new HashMap<>();
        String sql = "SELECT s.user_id, s.problem_id, s.status, p.difficulty, " +
                     "GROUP_CONCAT(DISTINCT pt.label) as tags " +
                     "FROM submissions s " +
                     "JOIN problems p ON s.problem_id = p.id " +
                     "LEFT JOIN problem_tag_relations ptr ON p.id = ptr.problem_id " +
                     "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
                     "GROUP BY s.user_id, s.problem_id, s.status, p.difficulty";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String userId = rs.getString("user_id");
                long problemId = rs.getLong("problem_id");
                String status = rs.getString("status");
                String difficulty = rs.getString("difficulty");
                String tagsStr = rs.getString("tags");

                UserData data = map.computeIfAbsent(userId, k -> new UserData());
                data.attempts++;

                Set<String> tags = new HashSet<>();
                if (tagsStr != null) {
                    for (String t : tagsStr.split(",")) tags.add(t.trim());
                }

                for (String tag : tags) {
                    data.tagAttempts.merge(tag, 1, Integer::sum);
                    if ("Accepted".equals(status)) {
                        data.tagAccepted.merge(tag, 1, Integer::sum);
                    }
                }

                if ("Accepted".equals(status) && !data.solvedProblems.contains(problemId)) {
                    data.solvedProblems.add(problemId);
                    data.solved++;
                    data.difficultyStats.merge(difficulty, 1, Integer::sum);
                }
            }
        }
        return map;
    }

    private UserProfile buildProfile(String userId, UserData data) {
        Map<String, Double> tagMastery = new HashMap<>();
        for (String tag : data.tagAttempts.keySet()) {
            int attempts = data.tagAttempts.get(tag);
            int accepted = data.tagAccepted.getOrDefault(tag, 0);
            tagMastery.put(tag, attempts > 0 ? (double) accepted / attempts : 0.0);
        }

        String level = getSkillLevel(data);
        int rating = switch (level) {
            case "beginner" -> 1000;
            case "intermediate" -> 1500;
            case "advanced" -> 2000;
            default -> 1200;
        };

        return UserProfile.builder()
                .userId(userId)
                .rating(rating)
                .maxRating(rating)
                .solvedProblems(data.solvedProblems)
                .tagMastery(tagMastery)
                .difficultyStats(data.difficultyStats)
                .totalSolved(data.solved)
                .totalAttempts(data.attempts)
                .build();
    }

    private String getSkillLevel(UserData data) {
        if (data.solved == 0) return "beginner";
        int easy = data.difficultyStats.getOrDefault("Easy", 0);
        int medium = data.difficultyStats.getOrDefault("Medium", 0);
        int hard = data.difficultyStats.getOrDefault("Hard", 0);

        double medRate = (double) medium / data.solved;
        double hardRate = (double) hard / data.solved;

        if (medRate >= 0.3 && hardRate >= 0.1) return "advanced";
        if (easy >= 0.4 && medRate >= 0.2) return "intermediate";
        return "beginner";
    }

    private RecommendEngine createEngine() {
        List<RecallStrategy> recall = new ArrayList<>();
        recall.add(new ColdStartStrategy(allProblems));
        recall.add(new HotRecallStrategy(allProblems));
        recall.add(new ContentRecallStrategy(allProblems));
        recall.add(new CFRecallStrategy(userProblemMatrix, allProblems));

        RankStrategy rank = new RuleRankStrategy();

        List<ReRankStrategy> rerank = new ArrayList<>();
        rerank.add(new DiversityReRankStrategy());
        rerank.add(new FreshnessReRankStrategy());

        return new RecommendEngine(recall, rank, rerank);
    }

    private EvaluationResult evaluate(RecommendEngine engine, UserProfile profile, UserData data) {
        RecommendContext context = RecommendContext.builder()
                .userId(profile.getUserId())
                .size(k * 2)
                .scenario(RecommendContext.Scenario.DAILY)
                .includeSolved(false)
                .build();

        List<RecommendItem> recs = engine.recommend(context, profile);

        Set<Long> relevant = getGroundTruth(profile);

        List<Long> recIds = recs.stream()
                .map(RecommendItem::getProblemId)
                .collect(Collectors.toList());

        // Use the correct method signature
        OfflineMetrics metrics = evaluator.evaluate(recIds, relevant, k, recs, allProblems.size());

        return new EvaluationResult(profile, recs, relevant, metrics, data);
    }

    private Set<Long> getGroundTruth(UserProfile profile) {
        Set<Long> relevant = new HashSet<>();

        // Determine appropriate difficulty based on user's skill level
        int easy = profile.getDifficultyStats().getOrDefault("Easy", 0);
        int medium = profile.getDifficultyStats().getOrDefault("Medium", 0);
        int hard = profile.getDifficultyStats().getOrDefault("Hard", 0);
        int total = profile.getTotalSolved();

        String diff;
        if (total == 0) {
            diff = "Easy";
        } else {
            double medRate = (double) medium / total;
            double hardRate = (double) hard / total;
            if (medRate >= 0.3 && hardRate >= 0.1) {
                diff = "Hard";
            } else if (medRate >= 0.2) {
                diff = "Medium";
            } else {
                diff = "Easy";
            }
        }

        for (RecommendItem p : allProblems) {
            if (profile.getSolvedProblems().contains(p.getProblemId())) continue;
            if (!diff.equals(p.getDifficulty())) continue;

            if (p.getTags() != null) {
                for (String tag : p.getTags()) {
                    Double m = profile.getTagMastery().get(tag);
                    if (m != null && m > 0.1 && m < 0.9) {
                        relevant.add(p.getProblemId());
                        break;
                    }
                }
            }
        }
        return relevant;
    }

    private void printResults(List<EvaluationResult> results) {
        System.out.println("------------------------------------------------------------");
        System.out.println("Individual Results");
        System.out.println("------------------------------------------------------------");

        for (EvaluationResult r : results) {
            Set<String> weak = new HashSet<>(), strong = new HashSet<>();
            for (var e : r.profile.getTagMastery().entrySet()) {
                if (e.getValue() < WEAK_TAG_THRESHOLD) weak.add(e.getKey());
                else if (e.getValue() > STRONG_TAG_THRESHOLD) strong.add(e.getKey());
            }

            System.out.println("\nUser: " + r.profile.getUserId());
            System.out.println("  Solved: " + r.data.solved + ", Attempts: " + r.data.attempts);
            System.out.println("  Level: " + getSkillLevel(r.data));
            System.out.println("  Weak: " + format(weak, 3));
            System.out.println("  Strong: " + format(strong, 3));
            System.out.printf("  P@%d: %.2f%%, R@%d: %.2f%%, F1: %.2f%%, NDCG: %.2f%%, Div: %.2f%%%n",
                    k, r.metrics.getPrecision() * 100,
                    k, r.metrics.getRecall() * 100,
                    r.metrics.getF1Score() * 100,
                    r.metrics.getNdcg() * 100,
                    r.metrics.getDiversity() * 100);
        }

        // Aggregate
        List<EvaluationInput> inputs = results.stream()
                .map(r -> EvaluationInput.builder()
                        .recommended(r.recs.stream().map(RecommendItem::getProblemId).collect(Collectors.toList()))
                        .relevant(r.relevant)
                        .k(k)
                        .items(r.recs)
                        .catalogSize(allProblems.size())
                        .build())
                .collect(Collectors.toList());

        OfflineMetrics agg = evaluator.evaluateAggregate(inputs);

        System.out.println("\n============================================================");
        System.out.println("Aggregate Results");
        System.out.println("============================================================");
        System.out.println("Users: " + results.size() + ", Catalog: " + allProblems.size());
        System.out.printf("Precision@%d: %.2f%%%n", k, agg.getPrecision() * 100);
        System.out.printf("Recall@%d:    %.2f%%%n", k, agg.getRecall() * 100);
        System.out.printf("F1-Score:     %.2f%%%n", agg.getF1Score() * 100);
        System.out.printf("NDCG@%d:      %.2f%%%n", k, agg.getNdcg() * 100);
        System.out.printf("Coverage:     %.2f%%%n", agg.getCoverage() * 100);
        System.out.printf("Diversity:    %.2f%%%n", agg.getDiversity() * 100);

        System.out.println("\n============================================================");
        System.out.println("Metrics Explanation");
        System.out.println("============================================================");
        System.out.println("Precision@K: Recommended items that are relevant");
        System.out.println("Recall@K:    Relevant items that are recommended");
        System.out.println("F1-Score:    Harmonic mean of P and R");
        System.out.println("NDCG@K:      Ranking quality");
        System.out.println("Coverage:    Catalog proportion recommended");
        System.out.println("Diversity:   Item dissimilarity");
    }

    private String format(Set<String> s, int limit) {
        if (s.isEmpty()) return "none";
        List<String> list = new ArrayList<>(s);
        String r = String.join(", ", list.subList(0, Math.min(limit, list.size())));
        return list.size() > limit ? r + ", ..." : r;
    }

    private static class UserData {
        int solved = 0, attempts = 0;
        Set<Long> solvedProblems = new HashSet<>();
        Map<String, Integer> difficultyStats = new HashMap<>();
        Map<String, Integer> tagAttempts = new HashMap<>();
        Map<String, Integer> tagAccepted = new HashMap<>();
    }

    private record EvaluationResult(
            UserProfile profile,
            List<RecommendItem> recs,
            Set<Long> relevant,
            OfflineMetrics metrics,
            UserData data
    ) {}
}
