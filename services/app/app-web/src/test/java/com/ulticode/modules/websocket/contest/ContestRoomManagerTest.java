package com.ulticode.modules.websocket.contest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for ContestRoomManager. */
class ContestRoomManagerTest {

  private ContestRoomManager manager;

  @BeforeEach
  void setUp() {
    manager = new ContestRoomManager();
  }

  @Test
  void subscribe_userToContest() {
    manager.subscribe("contest-1", "user-1");

    assertTrue(manager.isSubscribed("contest-1", "user-1"));
    assertEquals(1, manager.getSubscriberCount("contest-1"));
    assertTrue(manager.getSubscribers("contest-1").contains("user-1"));
    assertTrue(manager.getUserContests("user-1").contains("contest-1"));
  }

  @Test
  void unsubscribe_userFromContest() {
    manager.subscribe("contest-1", "user-1");
    manager.unsubscribe("contest-1", "user-1");

    assertFalse(manager.isSubscribed("contest-1", "user-1"));
    assertEquals(0, manager.getSubscriberCount("contest-1"));
    assertTrue(manager.getSubscribers("contest-1").isEmpty());
    assertTrue(manager.getUserContests("user-1").isEmpty());
  }

  @Test
  void unsubscribeAll_removesAllUserSubscriptions() {
    manager.subscribe("contest-1", "user-1");
    manager.subscribe("contest-2", "user-1");
    manager.subscribe("contest-1", "user-2");

    manager.unsubscribeAll("user-1");

    assertFalse(manager.isSubscribed("contest-1", "user-1"));
    assertFalse(manager.isSubscribed("contest-2", "user-1"));
    assertTrue(manager.isSubscribed("contest-1", "user-2"));
  }

  @Test
  void getSubscribers_multipleUsers() {
    manager.subscribe("contest-1", "user-1");
    manager.subscribe("contest-1", "user-2");
    manager.subscribe("contest-1", "user-3");

    Set<String> subscribers = manager.getSubscribers("contest-1");

    assertEquals(3, subscribers.size());
    assertTrue(subscribers.contains("user-1"));
    assertTrue(subscribers.contains("user-2"));
    assertTrue(subscribers.contains("user-3"));
  }

  @Test
  void getUserContests_multipleContests() {
    manager.subscribe("contest-1", "user-1");
    manager.subscribe("contest-2", "user-1");
    manager.subscribe("contest-3", "user-1");

    Set<String> contests = manager.getUserContests("user-1");

    assertEquals(3, contests.size());
    assertTrue(contests.contains("contest-1"));
    assertTrue(contests.contains("contest-2"));
    assertTrue(contests.contains("contest-3"));
  }

  @Test
  void getActiveContestCount() {
    manager.subscribe("contest-1", "user-1");
    manager.subscribe("contest-2", "user-1");
    manager.subscribe("contest-3", "user-2");

    assertEquals(3, manager.getActiveContestCount());
  }

  @Test
  void getActiveUserCount() {
    manager.subscribe("contest-1", "user-1");
    manager.subscribe("contest-2", "user-2");
    manager.subscribe("contest-3", "user-3");

    assertEquals(3, manager.getActiveUserCount());
  }

  @Test
  void getSubscribers_emptyContest() {
    Set<String> subscribers = manager.getSubscribers("nonexistent");

    assertTrue(subscribers.isEmpty());
  }

  @Test
  void getUserContests_emptyUser() {
    Set<String> contests = manager.getUserContests("nonexistent");

    assertTrue(contests.isEmpty());
  }

  @Test
  void isSubscribed_notSubscribed() {
    manager.subscribe("contest-1", "user-1");

    assertFalse(manager.isSubscribed("contest-1", "user-2"));
    assertFalse(manager.isSubscribed("contest-2", "user-1"));
  }
}
