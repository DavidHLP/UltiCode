package com.ulticode.modules.websocket.contest;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manager for contest room subscriptions.
 *
 * <p>Tracks which users are subscribed to which contest rooms.
 * This allows for efficient broadcasting to contest participants.
 */
@Component
public class ContestRoomManager {

  private static final Logger log = LoggerFactory.getLogger(ContestRoomManager.class);

  /** Map of contest ID to set of user IDs subscribed to that contest. */
  private final Map<String, Set<String>> contestSubscriptions = new ConcurrentHashMap<>();

  /** Map of user ID to set of contest IDs they are subscribed to. */
  private final Map<String, Set<String>> userSubscriptions = new ConcurrentHashMap<>();

  /**
   * Subscribe a user to a contest room.
   *
   * @param contestId the contest ID
   * @param userId the user ID
   */
  public void subscribe(String contestId, String userId) {
    contestSubscriptions
        .computeIfAbsent(contestId, k -> ConcurrentHashMap.newKeySet())
        .add(userId);

    userSubscriptions
        .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
        .add(contestId);

    log.debug("User {} subscribed to contest {}", userId, contestId);
  }

  /**
   * Unsubscribe a user from a contest room.
   *
   * @param contestId the contest ID
   * @param userId the user ID
   */
  public void unsubscribe(String contestId, String userId) {
    Set<String> contestUsers = contestSubscriptions.get(contestId);
    if (contestUsers != null) {
      contestUsers.remove(userId);
      if (contestUsers.isEmpty()) {
        contestSubscriptions.remove(contestId);
      }
    }

    Set<String> userContests = userSubscriptions.get(userId);
    if (userContests != null) {
      userContests.remove(contestId);
      if (userContests.isEmpty()) {
        userSubscriptions.remove(userId);
      }
    }

    log.debug("User {} unsubscribed from contest {}", userId, contestId);
  }

  /**
   * Unsubscribe a user from all contest rooms.
   *
   * @param userId the user ID
   */
  public void unsubscribeAll(String userId) {
    Set<String> userContests = userSubscriptions.remove(userId);
    if (userContests != null) {
      for (String contestId : userContests) {
        Set<String> contestUsers = contestSubscriptions.get(contestId);
        if (contestUsers != null) {
          contestUsers.remove(userId);
          if (contestUsers.isEmpty()) {
            contestSubscriptions.remove(contestId);
          }
        }
      }
    }

    log.debug("User {} unsubscribed from all contests", userId);
  }

  /**
   * Get all users subscribed to a contest.
   *
   * @param contestId the contest ID
   * @return set of user IDs (unmodifiable)
   */
  public Set<String> getSubscribers(String contestId) {
    Set<String> subscribers = contestSubscriptions.get(contestId);
    return subscribers != null
        ? Collections.unmodifiableSet(subscribers)
        : Collections.emptySet();
  }

  /**
   * Get all contests a user is subscribed to.
   *
   * @param userId the user ID
   * @return set of contest IDs (unmodifiable)
   */
  public Set<String> getUserContests(String userId) {
    Set<String> contests = userSubscriptions.get(userId);
    return contests != null
        ? Collections.unmodifiableSet(contests)
        : Collections.emptySet();
  }

  /**
   * Check if a user is subscribed to a contest.
   *
   * @param contestId the contest ID
   * @param userId the user ID
   * @return true if subscribed
   */
  public boolean isSubscribed(String contestId, String userId) {
    Set<String> subscribers = contestSubscriptions.get(contestId);
    return subscribers != null && subscribers.contains(userId);
  }

  /**
   * Get the total number of active contest rooms.
   *
   * @return number of contest rooms with at least one subscriber
   */
  public int getActiveContestCount() {
    return contestSubscriptions.size();
  }

  /**
   * Get the total number of unique users with active subscriptions.
   *
   * @return number of users
   */
  public int getActiveUserCount() {
    return userSubscriptions.size();
  }

  /**
   * Get the number of subscribers for a specific contest.
   *
   * @param contestId the contest ID
   * @return number of subscribers
   */
  public int getSubscriberCount(String contestId) {
    Set<String> subscribers = contestSubscriptions.get(contestId);
    return subscribers != null ? subscribers.size() : 0;
  }
}
