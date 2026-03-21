package com.ulticode.modules.websocket.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for UserSessionManager. */
class UserSessionManagerTest {

  private UserSessionManager manager;

  @BeforeEach
  void setUp() {
    manager = new UserSessionManager();
  }

  @Test
  void registerSession() {
    manager.registerSession("user-1", "session-1");

    assertTrue(manager.isUserOnline("user-1"));
    assertEquals("user-1", manager.getUserId("session-1"));
    assertTrue(manager.getUserSessions("user-1").contains("session-1"));
  }

  @Test
  void registerSession_multipleSessionsForSameUser() {
    manager.registerSession("user-1", "session-1");
    manager.registerSession("user-1", "session-2");

    assertTrue(manager.isUserOnline("user-1"));
    assertEquals(2, manager.getUserSessions("user-1").size());
    assertEquals(2, manager.getTotalSessionCount());
  }

  @Test
  void unregisterSession() {
    manager.registerSession("user-1", "session-1");
    manager.unregisterSession("session-1");

    assertFalse(manager.isUserOnline("user-1"));
    assertNull(manager.getUserId("session-1"));
    assertTrue(manager.getUserSessions("user-1").isEmpty());
  }

  @Test
  void unregisterSession_lastSessionRemovesUser() {
    manager.registerSession("user-1", "session-1");
    manager.registerSession("user-1", "session-2");

    manager.unregisterSession("session-1");

    assertTrue(manager.isUserOnline("user-1"));
    assertEquals(1, manager.getUserSessions("user-1").size());

    manager.unregisterSession("session-2");

    assertFalse(manager.isUserOnline("user-1"));
  }

  @Test
  void subscribeToCommunity() {
    manager.registerSession("user-1", "session-1");
    manager.subscribeToCommunity("user-1", "community-1");

    assertTrue(manager.isSubscribedToCommunity("user-1", "community-1"));
    assertTrue(manager.getUserCommunities("user-1").contains("community-1"));
  }

  @Test
  void unsubscribeFromCommunity() {
    manager.registerSession("user-1", "session-1");
    manager.subscribeToCommunity("user-1", "community-1");
    manager.unsubscribeFromCommunity("user-1", "community-1");

    assertFalse(manager.isSubscribedToCommunity("user-1", "community-1"));
    assertTrue(manager.getUserCommunities("user-1").isEmpty());
  }

  @Test
  void isUserOnline_falseForNonExistent() {
    assertFalse(manager.isUserOnline("nonexistent"));
  }

  @Test
  void getUserSessions_emptyForNonExistent() {
    Set<String> sessions = manager.getUserSessions("nonexistent");
    assertTrue(sessions.isEmpty());
  }

  @Test
  void getOnlineUserCount() {
    manager.registerSession("user-1", "session-1");
    manager.registerSession("user-2", "session-2");
    manager.registerSession("user-3", "session-3");

    assertEquals(3, manager.getOnlineUserCount());
  }

  @Test
  void getTotalSessionCount() {
    manager.registerSession("user-1", "session-1");
    manager.registerSession("user-1", "session-2");
    manager.registerSession("user-2", "session-3");

    assertEquals(3, manager.getTotalSessionCount());
  }

  @Test
  void unregisterSession_unsubscribesFromCommunities() {
    manager.registerSession("user-1", "session-1");
    manager.subscribeToCommunity("user-1", "community-1");
    manager.subscribeToCommunity("user-1", "community-2");

    manager.unregisterSession("session-1");

    assertTrue(manager.getUserCommunities("user-1").isEmpty());
  }
}
