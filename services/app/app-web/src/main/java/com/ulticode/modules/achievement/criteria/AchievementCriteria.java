package com.ulticode.modules.achievement.criteria;

import com.ulticode.modules.achievement.constants.AchievementType;

import java.util.Map;

/**
 * Deep in-process module that owns achievement criteria decoding and evaluation.
 *
 * <p>Concentrates the rules previously duplicated across the projection read path
 * (type extraction, target coercion, current-value resolution, percentage shaping,
 * and milestone tables) and the award write path (type match, target, and
 * eligibility). Producers decode the raw string-keyed criteria map once via
 * {@link #from(Map)} and query the resulting typed view; the milestone tables,
 * {@code type}&rarr;counter mapping, and {@code Number}&rarr;{@code int} coercion
 * stay hidden behind this class (architecture-review candidate 3).</p>
 *
 * <p>Behaviour is byte-for-byte identical to the previously inlined helpers in
 * {@code DefaultAchievementProjection} and {@code AchievementTriggerServiceImpl};
 * only locality changed. Legacy rows whose {@code criteria} column is {@code null}
 * decode to {@link #empty()}, yielding zero progress and never awarding.</p>
 *
 * @author UltiCode
 * @since 2026-07-15
 */
public final class AchievementCriteria {

    private static final int[] PROBLEMS_SOLVED_MILESTONES = {1, 10, 50, 100, 200, 500};
    private static final int[] SUBMISSIONS_MADE_MILESTONES = {1, 10, 50, 100, 500, 1000};

    private static final String KEY_TYPE = "type";
    private static final String KEY_TARGET = "target";
    private static final String TYPE_PROBLEMS_SOLVED = "problems_solved";
    private static final String TYPE_SUBMISSIONS_MADE = "submissions_made";
    private static final String UNIT_PROBLEMS = "problems";
    private static final String UNIT_SUBMISSIONS = "submissions";
    private static final String MAX_MILESTONE_REACHED = "Max milestone reached";

    private final String type;
    private final int target;

    private AchievementCriteria(String type, int target) {
        this.type = type;
        this.target = target;
    }

    /**
     * Empty criteria: no type and a zero target. Yields zero progress, a {@code null}
     * milestone, never matches an {@link AchievementType}, and awards only against a
     * zero target.
     *
     * @return a criteria view representing absent criteria
     */
    public static AchievementCriteria empty() {
        return new AchievementCriteria(null, 0);
    }

    /**
     * Decode a raw persisted criteria map into a typed view. A {@code null} map
     * (legacy seed data whose {@code criteria} column was non-JSON) decodes to
     * {@link #empty()}.
     *
     * @param raw the persisted criteria map, possibly {@code null}
     * @return the decoded criteria view, never {@code null}
     */
    public static AchievementCriteria from(Map<String, Object> raw) {
        if (raw == null) {
            return empty();
        }
        String type = (String) raw.get(KEY_TYPE);
        return new AchievementCriteria(type, readTarget(raw.get(KEY_TARGET)));
    }

    private static int readTarget(Object targetValue) {
        if (targetValue instanceof Number) {
            return ((Number) targetValue).intValue();
        }
        return 0;
    }

    /**
     * @return the raw criteria type string, or {@code null} when absent
     */
    public String type() {
        return type;
    }

    /**
     * Whether this criteria targets the given achievement type. Returns {@code false}
     * for empty criteria and for type strings that do not equal the enum value.
     *
     * @param achievementType the type to compare against
     * @return {@code true} when the decoded type matches the enum value
     */
    public boolean matches(AchievementType achievementType) {
        return type != null && type.equals(achievementType.getValue());
    }

    /**
     * @return the decoded target threshold, or {@code 0} when absent or non-numeric
     */
    public int target() {
        return target;
    }

    /**
     * Resolve the current value against the supplied read-side counters. Only the
     * {@code problems_solved} and {@code submissions_made} criteria types carry a
     * counter rule; every other (or absent) type resolves to {@code 0}.
     *
     * @param counters the pre-fetched aggregate counts
     * @return the current value for this criteria, never negative
     */
    public int currentValue(AchievementCounters counters) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case TYPE_PROBLEMS_SOLVED -> (int) counters.problemsSolved();
            case TYPE_SUBMISSIONS_MADE -> (int) counters.submissionsMade();
            default -> 0;
        };
    }

    /**
     * Clamp the supplied current value into a {@code [0, 100]} percentage of the
     * target. A zero target yields {@code 0} to avoid division by zero.
     *
     * @param currentValue the resolved current value
     * @return the integer percentage in {@code [0, 100]}
     */
    public int progressPercent(int currentValue) {
        return target > 0 ? Math.min(100, (currentValue * 100) / target) : 0;
    }

    /**
     * Whether the supplied current value meets the award threshold.
     *
     * @param currentValue the resolved current value
     * @return {@code true} when {@code currentValue >= target}
     */
    public boolean isMetBy(int currentValue) {
        return currentValue >= target;
    }

    /**
     * Describe the next milestone for the supplied current value, or {@code null}
     * when the criteria type owns no milestone table.
     *
     * @param currentValue the resolved current value
     * @return the next milestone label, or {@code null} for unknown types
     */
    public String nextMilestone(int currentValue) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case TYPE_PROBLEMS_SOLVED -> nextMilestone(currentValue, PROBLEMS_SOLVED_MILESTONES, UNIT_PROBLEMS);
            case TYPE_SUBMISSIONS_MADE -> nextMilestone(currentValue, SUBMISSIONS_MADE_MILESTONES, UNIT_SUBMISSIONS);
            default -> null;
        };
    }

    private static String nextMilestone(int currentValue, int[] milestones, String unit) {
        for (int milestone : milestones) {
            if (currentValue < milestone) {
                return milestone + " " + unit;
            }
        }
        return MAX_MILESTONE_REACHED;
    }
}
