package com.ulticode.notification.api.event;

/**
 * Wire contract for the App-published {@code NotificationIntentCreated}
 * integration event.
 *
 * <p>Version 1 deliberately keeps the existing flat JSON payload. Consumers
 * may ignore additional fields, but the common routing fields are stable and
 * must remain present. Keeping these names in the provider contract prevents
 * the future notification service from depending on App-private intent
 * classes.
 */
public final class NotificationIntentEventContract {

    public static final String EVENT_TYPE = "NotificationIntentCreated";
    public static final int SCHEMA_VERSION = 1;
    public static final String OWNER = "App";

    public static final String INTENT_TYPE = "intentType";
    public static final String INTENT_ID = "intentId";
    public static final String USER_ID = "userId";
    public static final String CATEGORY = "category";

    public static final String SUBMISSION_ID = "submissionId";
    public static final String GENERATION = "generation";
    public static final String STATUS = "status";
    public static final String PROBLEM_ID = "problemId";
    public static final String PROBLEM_TITLE = "problemTitle";
    public static final String ELAPSED_MS = "elapsedMs";
    public static final String MEMORY_BYTES = "memoryBytes";
    public static final String CONTEST_ID = "contestId";
    public static final String CONTEST_TITLE = "contestTitle";
    public static final String CONTEST_SCORE_DELTA = "contestScoreDelta";

    public static final String ACHIEVEMENT_ID = "achievementId";
    public static final String ACHIEVEMENT_KEY = "achievementKey";
    public static final String ACHIEVEMENT_NAME = "achievementName";
    public static final String ACHIEVEMENT_DESCRIPTION = "achievementDescription";
    public static final String ACHIEVEMENT_ICON_URL = "achievementIconUrl";
    public static final String ACHIEVEMENT_TIER = "achievementTier";
    public static final String POINTS = "points";
    public static final String EARNED_AT = "earnedAt";

    public static final String START_TIME = "startTime";
    public static final String REMINDER_TYPE = "reminderType";
    public static final String FOLLOWER_USER_ID = "followerUserId";
    public static final String FOLLOWER_USERNAME = "followerUsername";
    public static final String FOLLOW_DAY = "followDay";
    public static final String COMMENT_ID = "commentId";
    public static final String REPLIER_USER_ID = "replierUserId";
    public static final String REPLIER_USERNAME = "replierUsername";
    public static final String PREVIEW = "preview";
    public static final String LINK = "link";
    public static final String ALERT_KEY = "alertKey";
    public static final String TITLE = "title";
    public static final String BODY = "body";

    private NotificationIntentEventContract() {
    }
}
