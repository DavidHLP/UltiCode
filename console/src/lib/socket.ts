import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

/**
 * WebSocket event types matching backend WebSocketConstants
 */
export enum NotificationEvent {
  // Submission events
  SUBMISSION_RESULT = "submission_result",
  SUBMISSION_STARTED = "submission_started",

  // Contest events
  CONTEST_UPDATE = "contest_status",
  CONTEST_RANKING_CHANGE = "ranking_update",
  CONTEST_STARTING = "contest_starting",
  CONTEST_ENDED = "contest_ended",

  // Community events
  COMMUNITY_NEW_POST = "community_new_post",
  COMMUNITY_NEW_COMMENT = "community_new_comment",
  COMMUNITY_POST_LIKED = "community_post_liked",

  // User interaction events
  MENTION_USER = "mention_user",
  REPLY_TO_POST = "reply_to_post",
  LIKE_SOLUTION = "like_solution",

  // Achievement events
  BADGE_EARNED = "badge_earned",
  MILESTONE_REACHED = "milestone_reached",

  // System events
  SYSTEM_ANNOUNCEMENT = "announcement",
  MAINTENANCE_WARNING = "maintenance_warning",

  // Connection events (client-side only)
  CONNECTED = "connected",
  DISCONNECT = "disconnect",
  CONNECT_ERROR = "connect_error",
  CONNECTION_STATUS = "connection:status",
}

export interface SubmissionResultPayload {
  submissionId: string;
  problemId: string;
  problemSlug: string;
  status: string;
  runtime: number;
  memory: number;
}

export interface ContestUpdatePayload {
  contestId: string;
  type: "ranking_change" | "problem_solved" | "contest_update";
  data: unknown;
}

export interface BadgeEarnedPayload {
  badgeId: string;
  badgeName: string;
  badgeDescription: string;
  earnedAt: string;
}

export interface NotificationPayload {
  id: string;
  type: string;
  title: string;
  content: string;
  link?: string;
  createdAt: string;
}

export interface CommunityPostPayload {
  postId: string;
  postTitle: string;
  communityId: string;
  communityName: string;
  authorId: string;
  authorName: string;
  excerpt: string;
}

export interface CommunityCommentPayload {
  commentId: string;
  postId: string;
  postTitle: string;
  communityId: string;
  authorId: string;
  authorName: string;
  content: string;
}

export interface WebSocketMessage<T = unknown> {
  event: NotificationEvent;
  data: T;
  timestamp: number;
}

export type ConnectionStatus =
  | "connected"
  | "disconnected"
  | "connecting"
  | "reconnecting";

type EventCallback<T = unknown> = (data: T) => void;

interface SocketManager {
  status: ConnectionStatus;
  connect: () => void;
  disconnect: () => void;
  on: <T>(
    event: NotificationEvent | string,
    callback: EventCallback<T>,
  ) => void;
  off: <T>(
    event: NotificationEvent | string,
    callback: EventCallback<T>,
  ) => void;
  subscribeToContest: (contestId: string) => void;
  unsubscribeFromContest: (contestId: string) => void;
  subscribeToCommunity: (communityId: string) => void;
  unsubscribeFromCommunity: (communityId: string) => void;
}

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:9001";

// Singleton socket manager
let socketInstance: SocketManager | null = null;
const eventListeners = new Map<string, Set<EventCallback>>();
const subscriptions = new Map<string, StompSubscription>();

/**
 * Get CSRF token — delegates to the canonical implementation in csrf.ts
 * which supports both in-memory and cookie-based token storage.
 */
import { getCsrfToken } from "@/shared/auth-core/src";

function createSocketManager(): SocketManager {
  let client: Client | null = null;
  let status: ConnectionStatus = "disconnected";
  let reconnectAttempts = 0;
  const maxReconnectAttempts = 5;

  const notifyStatusChange = (newStatus: ConnectionStatus) => {
    status = newStatus;
    const callbacks = eventListeners.get(NotificationEvent.CONNECTION_STATUS);
    if (callbacks) {
      callbacks.forEach((cb) => cb(newStatus));
    }
  };

  const handleMessage = (event: string, message: IMessage) => {
    try {
      const body = JSON.parse(message.body);
      const callbacks = eventListeners.get(event);
      if (callbacks) {
        callbacks.forEach((cb) => cb(body));
      }
    } catch (error) {
      console.error(`[WebSocket] Error parsing message for ${event}:`, error);
    }
  };

  const connect = () => {
    if (client?.connected) return;

    notifyStatusChange("connecting");

    const csrfToken = getCsrfToken();

    // Auth relies on httpOnly cookies sent automatically by SockJS (withCredentials).
    // Do NOT attempt to read access_token from document.cookie — httpOnly prevents JS access.
    client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws/notifications`),
      connectHeaders: {
        "X-CSRF-Token": csrfToken || "",
      },
      debug: () => {},
      reconnectDelay: 1000,
      maxReconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        notifyStatusChange("connected");
        reconnectAttempts = 0;

        // Notify connection listeners
        const callbacks = eventListeners.get(NotificationEvent.CONNECTED);
        if (callbacks) {
          callbacks.forEach((cb) => cb({ connected: true }));
        }

        // Subscribe to user-specific notification queue
        const notifSub = client?.subscribe(
          "/user/queue/notification",
          (message) =>
            handleMessage(NotificationEvent.SYSTEM_ANNOUNCEMENT, message),
        );
        if (notifSub) {
          subscriptions.set("notification", notifSub);
        }

        // Subscribe to submission results
        const submissionSub = client?.subscribe(
          "/user/queue/submission",
          (message) =>
            handleMessage(NotificationEvent.SUBMISSION_RESULT, message),
        );
        if (submissionSub) {
          subscriptions.set("submission", submissionSub);
        }

        // Subscribe to errors
        const errorSub = client?.subscribe("/user/queue/errors", (message) => {
          console.error("[WebSocket] Server error:", message.body);
          const callbacks = eventListeners.get(NotificationEvent.CONNECT_ERROR);
          if (callbacks) {
            callbacks.forEach((cb) => cb({ error: message.body }));
          }
        });
        if (errorSub) {
          subscriptions.set("errors", errorSub);
        }
      },
      onDisconnect: () => {
        notifyStatusChange("disconnected");
        const callbacks = eventListeners.get(NotificationEvent.DISCONNECT);
        if (callbacks) {
          callbacks.forEach((cb) => cb({ reason: "disconnected" }));
        }
      },
      onStompError: (frame) => {
        console.error("[WebSocket] STOMP error:", frame);
        const errorBody = frame.body || "";
        const isAuthError =
          errorBody.includes("WEBSOCKET_UNAUTHORIZED") ||
          errorBody.includes("WEBSOCKET_INVALID_TOKEN") ||
          errorBody.includes("No authentication token");

        if (isAuthError) {
          // Auth error means user is not logged in - stop reconnecting
          reconnectAttempts = maxReconnectAttempts; // Prevent further reconnect
          // Deactivate to stop reconnection attempts
          if (client) {
            client.deactivate();
          }
        }

        notifyStatusChange("disconnected");
        const callbacks = eventListeners.get(NotificationEvent.CONNECT_ERROR);
        if (callbacks) {
          callbacks.forEach((cb) => cb({ error: frame.body }));
        }
      },
      onWebSocketError: (event) => {
        console.error("[WebSocket] WebSocket error:", event);
        reconnectAttempts++;

        if (reconnectAttempts >= maxReconnectAttempts) {
          console.error("[WebSocket] Max reconnect attempts reached, stopping");
          notifyStatusChange("disconnected");
          reconnectAttempts = maxReconnectAttempts; // Ensure no more reconnects
        } else {
          notifyStatusChange("reconnecting");
        }
      },
      onWebSocketClose: () => {
        if (status === "connected") {
          notifyStatusChange("disconnected");
        }
      },
    });

    client.activate();
  };

  const disconnect = () => {
    // Unsubscribe from all subscriptions
    subscriptions.forEach((sub) => sub.unsubscribe());
    subscriptions.clear();

    if (client) {
      client.deactivate();
      client = null;
      notifyStatusChange("disconnected");
    }
  };

  const on = <T>(
    event: NotificationEvent | string,
    callback: EventCallback<T>,
  ) => {
    if (!eventListeners.has(event)) {
      eventListeners.set(event, new Set());
    }
    eventListeners.get(event)!.add(callback as EventCallback);
  };

  const off = <T>(
    event: NotificationEvent | string,
    callback: EventCallback<T>,
  ) => {
    const callbacks = eventListeners.get(event);
    if (callbacks) {
      callbacks.delete(callback as EventCallback);
    }
  };

  const subscribeToContest = (contestId: string) => {
    if (!client?.connected) {
      return;
    }

    // Unsubscribe from existing contest subscription if any
    const existingKey = `contest-${contestId}`;
    const existingSub = subscriptions.get(existingKey);
    if (existingSub) {
      existingSub.unsubscribe();
    }

    // Subscribe to contest topic
    const sub = client.subscribe(`/topic/contest/${contestId}`, (message) =>
      handleMessage(NotificationEvent.CONTEST_UPDATE, message),
    );
    subscriptions.set(existingKey, sub);

    // Send join message to server
    client.publish({
      destination: `/app/contest.join`,
      body: contestId,
    });
  };

  const unsubscribeFromContest = (contestId: string) => {
    const key = `contest-${contestId}`;
    const sub = subscriptions.get(key);
    if (sub) {
      sub.unsubscribe();
      subscriptions.delete(key);
    }

    // Send leave message to server
    if (client?.connected) {
      client.publish({
        destination: `/app/contest.leave`,
        body: contestId,
      });
    }
  };

  const subscribeToCommunity = (communityId: string) => {
    if (!client?.connected) {
      return;
    }

    const key = `community-${communityId}`;
    const existingSub = subscriptions.get(key);
    if (existingSub) {
      existingSub.unsubscribe();
    }

    const sub = client.subscribe(`/topic/community/${communityId}`, (message) =>
      handleMessage(NotificationEvent.COMMUNITY_NEW_POST, message),
    );
    subscriptions.set(key, sub);
  };

  const unsubscribeFromCommunity = (communityId: string) => {
    const key = `community-${communityId}`;
    const sub = subscriptions.get(key);
    if (sub) {
      sub.unsubscribe();
      subscriptions.delete(key);
    }
  };

  return {
    get status() {
      return status;
    },
    connect,
    disconnect,
    on,
    off,
    subscribeToContest,
    unsubscribeFromContest,
    subscribeToCommunity,
    unsubscribeFromCommunity,
  };
}

export function getSocketManager(): SocketManager {
  if (!socketInstance) {
    socketInstance = createSocketManager();
  }
  return socketInstance;
}

export { type SocketManager as SocketManagerType };
