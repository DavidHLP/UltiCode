import { io, type Socket } from "socket.io-client";

/**
 * WebSocket event types matching backend NotificationEvent enum
 */
export enum NotificationEvent {
  // Submission events
  SUBMISSION_RESULT = "submission:result",
  SUBMISSION_STARTED = "submission:started",

  // Contest events
  CONTEST_UPDATE = "contest:update",
  CONTEST_RANKING_CHANGE = "contest:ranking",
  CONTEST_STARTING = "contest:starting",
  CONTEST_ENDED = "contest:ended",

  // Community events
  COMMUNITY_NEW_POST = "community:new_post",
  COMMUNITY_NEW_COMMENT = "community:new_comment",
  COMMUNITY_POST_LIKED = "community:post_liked",

  // User interaction events
  MENTION_USER = "mention:user",
  REPLY_TO_POST = "post:reply",
  LIKE_SOLUTION = "solution:like",

  // Achievement events
  BADGE_EARNED = "badge:earned",
  MILESTONE_REACHED = "milestone:reached",

  // System events
  SYSTEM_ANNOUNCEMENT = "system:announcement",
  MAINTENANCE_WARNING = "system:maintenance",
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
  body: string;
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
  socket: Socket | null;
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
  emit: (event: string, data?: unknown) => void;
  subscribeToContest: (contestId: string) => void;
  unsubscribeFromContest: (contestId: string) => void;
  subscribeToCommunity: (communityId: string) => void;
  unsubscribeFromCommunity: (communityId: string) => void;
}

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:9001";
const WS_URL = API_BASE_URL.replace(/^http/, "ws");

// Singleton socket manager
let socketInstance: SocketManager | null = null;
const eventListeners = new Map<string, Set<EventCallback>>();

function createSocketManager(): SocketManager {
  let socket: Socket | null = null;
  let status: ConnectionStatus = "disconnected";
  const maxReconnectAttempts = 5;

  const notifyStatusChange = (newStatus: ConnectionStatus) => {
    status = newStatus;
    const callbacks = eventListeners.get("connection:status");
    if (callbacks) {
      callbacks.forEach((cb) => cb(newStatus));
    }
  };

  const connect = () => {
    if (socket?.connected) return;

    notifyStatusChange("connecting");

    // Get JWT token from cookies for authentication
    const getCookie = (name: string): string | null => {
      const value = `; ${document.cookie}`;
      const parts = value.split(`; ${name}=`);
      if (parts.length === 2) return parts.pop()?.split(";").shift() || null;
      return null;
    };

    const token = getCookie("access_token");

    socket = io(`${WS_URL}/notifications`, {
      auth: { token },
      withCredentials: true,
      transports: ["websocket", "polling"],
      reconnection: true,
      reconnectionAttempts: maxReconnectAttempts,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 5000,
    });

    socket.on("connect", () => {
      notifyStatusChange("connected");
    });

    socket.on("connected", (data) => {
      const callbacks = eventListeners.get("connected");
      if (callbacks) callbacks.forEach((cb) => cb(data));
    });

    socket.on("disconnect", (reason) => {
      notifyStatusChange("disconnected");
      const callbacks = eventListeners.get("disconnect");
      if (callbacks) callbacks.forEach((cb) => cb(reason));
    });

    socket.on("connect_error", (error) => {
      notifyStatusChange("reconnecting");
      const callbacks = eventListeners.get("connect_error");
      if (callbacks) callbacks.forEach((cb) => cb(error));
    });

    // Register event listeners for all notification types
    Object.values(NotificationEvent).forEach((event) => {
      socket?.on(event, (message: WebSocketMessage) => {
        const callbacks = eventListeners.get(event);
        if (callbacks) callbacks.forEach((cb) => cb(message.data));
      });
    });
  };

  const disconnect = () => {
    if (socket) {
      socket.disconnect();
      socket = null;
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

  const emit = (event: string, data?: unknown) => {
    socket?.emit(event, data);
  };

  const subscribeToContest = (contestId: string) => {
    emit("subscribe:contest", contestId);
  };

  const unsubscribeFromContest = (contestId: string) => {
    emit("unsubscribe:contest", contestId);
  };

  const subscribeToCommunity = (communityId: string) => {
    emit("subscribe:community", communityId);
  };

  const unsubscribeFromCommunity = (communityId: string) => {
    emit("unsubscribe:community", communityId);
  };

  return {
    get socket() {
      return socket;
    },
    get status() {
      return status;
    },
    connect,
    disconnect,
    on,
    off,
    emit,
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
