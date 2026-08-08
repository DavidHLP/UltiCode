package com.ulticode.modules.achievement.constants;

/**
 * Achievement type enumeration.
 *
 * <p>Defines the different types of achievements that can be earned.
 */
public enum AchievementType {
  /** Problems solved count */
  PROBLEMS_SOLVED("problems_solved"),
  /** Submissions made count */
  SUBMISSIONS_MADE("submissions_made"),
  /** Contest participation count */
  CONTEST_PARTICIPATION("contest_participation"),
  /** Contest wins count */
  CONTEST_WINS("contest_wins"),
  /** Contest placed (top N) count */
  CONTEST_PLACED("contest_placed"),
  /** Forum posts count */
  FORUM_POSTS("forum_posts"),
  /** Solutions written count */
  SOLUTIONS_WRITTEN("solutions_written"),
  /** Streak days count */
  STREAK_DAYS("streak_days"),
  /** Rating milestone reached */
  RATING_MILESTONE("rating_milestone"),
  /** Community contributor achievements */
  COMMUNITY_CONTRIBUTOR("community_contributor"),
  /** Follower count */
  FOLLOWER_COUNT("follower_count"),
  /** First problem solved */
  FIRST_PROBLEM("first_problem"),
  /** Language milestone for solved problems */
  LANGUAGE_SOLVED("language_solved");

  private final String value;

  AchievementType(String value) {
    this.value = value;
  }

  /**
   * Get the string value of the achievement type.
   *
   * @return the string value
   */
  public String getValue() {
    return value;
  }

  /**
   * Parse a string value to AchievementType.
   *
   * @param value the string value
   * @return the AchievementType or null if not found
   */
  public static AchievementType fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (AchievementType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    return null;
  }
}
