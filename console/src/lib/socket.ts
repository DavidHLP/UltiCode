import type { IMessage } from "@stomp/stompjs";
import { getCsrfToken } from "@/shared/auth-core/src";
import {
  createRealtimeTransport,
  type ConnectionStatus,
  type RealtimeTransport,
} from "@/lib/realtime/transport";

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

export type { ConnectionStatus };

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

/**
 * Notification channel adapter over the deep realtime transport. Owns the
 * three user-queue subscriptions established on every connect and the
 * notification-specific STOMP-error classification (auth errors stop the
 * reconnect loop). Endpoint: /ws/notifications; reconnect delegated to the
 * @stomp/stompjs built-in schedule.
 */
let notificationTransport: RealtimeTransport | null = null;
function getNotificationTransport(): RealtimeTransport {
  if (notificationTransport) return notificationTransport;
  notificationTransport = createRealtimeTransport({
    endpoint: "/ws/notifications",
    apiBaseUrl: API_BASE_URL,
    getCsrfToken,
    logTag: "WebSocket",
    reconnect: {
      kind: "library",
      reconnectDelay: 1000,
      maxReconnectDelay: 5000,
      maxAttempts: 5,
    },
    onConnect: (t) => {
      t.subscribe(
        "notification",
        "/user/queue/notification",
        (message: IMessage) =>
          t.dispatch(NotificationEvent.SYSTEM_ANNOUNCEMENT, message),
      );
      t.subscribe(
        "submission",
        "/user/queue/submission",
        (message: IMessage) =>
          t.dispatch(NotificationEvent.SUBMISSION_RESULT, message),
      );
      t.subscribe("errors", "/user/queue/errors", (message: IMessage) => {
        console.error("[WebSocket] Server error:", message.body);
        t.emit(NotificationEvent.CONNECT_ERROR, { error: message.body });
      });
    },
    classifyStompError: (body) => {
      if (
        body.includes("WEBSOCKET_UNAUTHORIZED") ||
        body.includes("WEBSOCKET_INVALID_TOKEN") ||
        body.includes("No authentication token")
      ) {
        return "auth";
      }
      return undefined;
    },
  });
  return notificationTransport;
}

/**
 * Build the public {@link SocketManager} facade over the notification
 * transport. Lifecycle, listener registry, and JSON parsing live in the
 * transport; this object only maps the legacy contest/community subscription
 * helpers onto transport.subscribe + publish.
 */
function createSocketManager(): SocketManager {
  const transport = getNotificationTransport();

  return {
    get status() {
      return transport.status;
    },
    connect: () => transport.connect(),
    disconnect: () => transport.disconnect(),
    on: (event, callback) => transport.on(event, callback as EventCallback),
    off: (event, callback) => transport.off(event, callback as EventCallback),
    subscribeToContest: (contestId: string) => {
      if (!transport.isConnected()) return;
      const key = `contest-${contestId}`;
      transport.unsubscribeKey(key);
      transport.subscribe(
        key,
        `/topic/contest/${contestId}`,
        (message: IMessage) =>
          transport.dispatch(NotificationEvent.CONTEST_UPDATE, message),
      );
      transport.publish("/app/contest.join", contestId);
    },
    unsubscribeFromContest: (contestId: string) => {
      transport.unsubscribeKey(`contest-${contestId}`);
      if (transport.isConnected()) {
        transport.publish("/app/contest.leave", contestId);
      }
    },
    subscribeToCommunity: (communityId: string) => {
      if (!transport.isConnected()) return;
      const key = `community-${communityId}`;
      transport.unsubscribeKey(key);
      transport.subscribe(
        key,
        `/topic/community/${communityId}`,
        (message: IMessage) =>
          transport.dispatch(NotificationEvent.COMMUNITY_NEW_POST, message),
      );
    },
    unsubscribeFromCommunity: (communityId: string) => {
      transport.unsubscribeKey(`community-${communityId}`);
    },
  };
}

// Singleton socket manager
let socketInstance: SocketManager | null = null;

export function getSocketManager(): SocketManager {
  if (!socketInstance) {
    socketInstance = createSocketManager();
  }
  return socketInstance;
}

export { type SocketManager as SocketManagerType };
