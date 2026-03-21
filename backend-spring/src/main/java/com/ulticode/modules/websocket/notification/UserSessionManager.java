package com.ulticode.modules.websocket.notification;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manager for user WebSocket sessions.
 *
 * <p>Tracks user sessions and their subscriptions to enable targeted messaging.
 */
@Component
public class UserSessionManager {

  private static final Logger log = LoggerFactory.getLogger(UserSessionManager.class);

  /** Map of user ID to set of session IDs. */
  private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

  /** Map of session ID to user ID. */
  private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

  /** Map of user ID to set of subscribed community IDs. */
  private final Map<String, Set<String>> userCommunitySubscriptions = new ConcurrentHashMap<>();

  /**
   * Register a new session for a user.
   *
   * @param userId the user ID
   * @param sessionId the WebSocket session ID
   */
  public void registerSession(String userId, String sessionId) {
    userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    sessionToUser.put(sessionId, userId);
    log.debug("Registered session {} for user {}", sessionId, userId);
  }

  /**
   * Unregister a session.
   *
   * @param sessionId the WebSocket session ID
   */
  public void unregisterSession(String sessionId) {
    String userId = sessionToUser.remove(sessionId);
    if (userId != null) {
      Set<String> sessions = userSessions.get(userId);
      if (sessions != null) {
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
          userSessions.remove(userId);
          userCommunitySubscriptions.remove(userId);
        }
      }
      log.debug("Unregistered session {} for user {}", sessionId, userId);
    }
  }

  /**
   * Subscribe a user to community updates.
   *
   * @param userId the user ID
   * @param communityId the community ID
   */
  public void subscribeToCommunity(String userId, String communityId) {
    userCommunitySubscriptions
        .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
        .add(communityId);
    log.debug("User {} subscribed to community {}", userId, communityId);
  }

  /**
   * Unsubscribe a user from community updates.
   *
   * @param userId the user ID
   * @param communityId the community ID
   */
  public void unsubscribeFromCommunity(String userId, String communityId) {
    Set<String> communities = userCommunitySubscriptions.get(userId);
    if (communities != null) {
      communities.remove(communityId);
      if (communities.isEmpty()) {
        userCommunitySubscriptions.remove(userId);
      }
    }
    log.debug("User {} unsubscribed from community {}", userId, communityId);
  }

  /**
   * Check if a user is online (has at least one active session).
   *
   * @param userId the user ID
   * @return true if online
   */
  public boolean isUserOnline(String userId) {
    Set<String> sessions = userSessions.get(userId);
    return sessions != null && !sessions.isEmpty();
  }

  /**
   * Get all sessions for a user.
   *
   * @param userId the user ID
   * @return set of session IDs (unmodifiable)
   */
  public Set<String> getUserSessions(String userId) {
    Set<String> sessions = userSessions.get(userId);
    return sessions != null ? Collections.unmodifiableSet(sessions) : Collections.emptySet();
  }

  /**
   * Get the user ID for a session.
   *
   * @param sessionId the session ID
   * @return the user ID or null if not found
   */
  public String getUserId(String sessionId) {
    return sessionToUser.get(sessionId);
  }

  /**
   * Get all communities a user is subscribed to.
   *
   * @param userId the user ID
   * @return set of community IDs (unmodifiable)
   */
  public Set<String> getUserCommunities(String userId) {
    Set<String> communities = userCommunitySubscriptions.get(userId);
    return communities != null ? Collections.unmodifiableSet(communities) : Collections.emptySet();
  }

  /**
   * Check if a user is subscribed to a community.
   *
   * @param userId the user ID
   * @param communityId the community ID
   * @return true if subscribed
   */
  public boolean isSubscribedToCommunity(String userId, String communityId) {
    Set<String> communities = userCommunitySubscriptions.get(userId);
    return communities != null && communities.contains(communityId);
  }

  /**
   * Get the total number of online users.
   *
   * @return number of users with at least one active session
   */
  public int getOnlineUserCount() {
    return userSessions.size();
  }

  /**
   * Get the total number of active sessions.
   *
   * @return number of sessions
   */
  public int getTotalSessionCount() {
    return sessionToUser.size();
  }
}
