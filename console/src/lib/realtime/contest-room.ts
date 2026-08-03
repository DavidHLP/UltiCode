// console/src/lib/realtime/contest-room.ts
import type { IMessage } from "@stomp/stompjs";
import { getCsrfToken } from "@/shared/auth-core/src";
import type { RankingEntry } from "@/types/contest";
import {
  createRealtimeTransport,
  type ConnectionStatus,
  type RealtimeTransport,
} from "@/lib/realtime/transport";

/**
 * Deep Contest realtime room module.
 *
 * Sits ABOVE the deep {@link RealtimeTransport} and owns every contest-room
 * concern so the Vue composable ({@link useContestSocket}) stops exposing
 * transport plumbing, room lifecycle, concurrency cleanup, message parsing,
 * and the six typed room events at a single seam. The transport stays the
 * internal seam it already was; this module is the room.
 *
 * Owns as named depth: the contest-channel transport adapter (singleton),
 * room lifecycle (join with the join-during-connect dance, leave), message
 * parse + dispatch, and typed room-event registration. Framework-agnostic:
 * no Vue import. The composable binds it to refs and component lifecycle;
 * tests can drive it with an injected in-memory transport. Behavior is
 * preserved exactly from the legacy inline composable.
 */

export type { ConnectionStatus };

/** Response from join/leave contest operations. */
export interface ContestRoomResponse {
  success: boolean;
  contestId: string;
  message: string;
  error?: string;
}

/** Ranking update event payload from server. */
export interface RankingUpdatePayload {
  contestId: string;
  rankings: RankingEntry[];
  updatedAt: Date | string;
}

/** First solve notification payload from server. */
export interface FirstSolvePayload {
  contestId: string;
  problemId: string;
  problemTitle: string;
  userId: string;
  username: string;
  solvedAt: Date | string;
}

/** Announcement payload from server. */
export interface AnnouncementPayload {
  id: string;
  contestId: string;
  title: string;
  content: string;
  createdAt: Date | string;
}

/** Contest status update payload from server. */
export interface ContestStatusPayload {
  contestId: string;
  status: "upcoming" | "registration" | "running" | "ended";
  startedAt?: Date | string;
  endsAt?: Date | string;
  message?: string;
}

/** Submission result payload from server. */
export interface SubmissionResultPayload {
  submissionId: string;
  contestId: string;
  problemId: string;
  userId: string;
  status: string;
  score: number;
  timeUsed?: number;
  memoryUsed?: number;
  judgedAt: Date | string;
}

// ─── Typed room events ────────────────────────────────────────────────

type RoomEventListener<T> = (data: T) => void;
type EventHandler = (...args: unknown[]) => void;

const RANKING_UPDATE = "ranking_update" as const;
const FIRST_SOLVE = "first_solve" as const;
const ANNOUNCEMENT = "announcement" as const;
const CONTEST_STATUS = "contest_status" as const;
const SUBMISSION_RESULT = "submission_result" as const;
const CONNECTION_STATUS = "connection:status" as const;

const JOIN_TIMEOUT_MS = 10_000;
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "/api";

// ─── Contest-channel transport adapter (singleton) ────────────────────

let contestTransport: RealtimeTransport | null = null;

/**
 * Contest channel adapter over the deep realtime transport. Endpoint
 * /ws/contest; manual exponential backoff (1s -> 2s -> 4s -> ... -> 30s, max
 * 10 attempts) owned by the transport. OnConnect (re)subscribes the broadcast
 * topic then fires the one-shot "ready" event that {@link ContestRoom.join}
 * uses for the join-during-connect dance. STOMP ERROR frames carrying
 * FORBIDDEN / 403 / "not registered" are classified as "rejected" (F-43).
 */
export function getContestTransport(): RealtimeTransport {
  if (contestTransport) return contestTransport;
  contestTransport = createRealtimeTransport({
    endpoint: "/ws/contest",
    apiBaseUrl: API_BASE_URL,
    getCsrfToken,
    logTag: "STOMP Contest",
    reconnect: {
      kind: "exponential",
      baseDelay: 1000,
      maxDelay: 30_000,
      maxAttempts: 10,
    },
    onConnect: (t) => {
      // General broadcast topic — must be (re)established before the
      // room-lifecycle "ready" hooks fire so join inherits a connected,
      // broadcast-subscribed client.
      t.subscribe("broadcast", "/topic/broadcast", (message: IMessage) =>
        t.dispatch(ANNOUNCEMENT, message),
      );
      // Fire one-shot room-lifecycle hooks. The transport is the single owner
      // of the connect handler — join registers on "ready" instead of
      // overwriting client.onConnect (the old override clobbered the status
      // notification + broadcast subscription, and the parallel
      // "connected_once" callback was never fired — dead code).
      t.emit("ready");
    },
    classifyStompError: (body) => {
      if (
        body.includes("FORBIDDEN") ||
        body.includes("403") ||
        body.toLowerCase().includes("not registered")
      ) {
        return "rejected";
      }
      return undefined;
    },
  });
  return contestTransport;
}

/** Reset the singleton transport — test-only hook. */
export function __resetContestTransportForTests(): void {
  contestTransport = null;
}

// ─── ContestRoom ──────────────────────────────────────────────────────

/**
 * One contest room session over a {@link RealtimeTransport}. Per-instance
 * room state (current contest id, pending join cleanup); the transport is the
 * shared singleton so multiple sessions reuse one STOMP connection.
 */
export class ContestRoom {
  private readonly transport: RealtimeTransport;
  private currentContestId: string | null = null;
  private pendingJoinCleanup: (() => void) | null = null;

  constructor(transport: RealtimeTransport = getContestTransport()) {
    this.transport = transport;
  }

  /** Current transport connection status. */
  get status(): ConnectionStatus {
    return this.transport.status;
  }

  /** Current contest id, or null when not in a room. */
  get contestId(): string | null {
    return this.currentContestId;
  }

  /** Whether the underlying transport reports connected. */
  isConnected(): boolean {
    return this.transport.isConnected();
  }

  /** Connect the underlying transport. */
  connect(): void {
    this.transport.connect();
  }

  /** Disconnect the underlying transport and forget the current room. */
  disconnect(): void {
    this.transport.disconnect();
    this.currentContestId = null;
  }

  /**
   * Join a contest room.
   *
   * Fast path: if the transport is already connected, subscribe + publish
   * synchronously. Slow path: wait for the transport's one-shot "ready" event
   * (fired from onConnect) with a 10s timeout. Never overwrites the
   * transport's connect handler. The pending ready hook + timeout are tracked
   * for {@link dispose} so an unmount mid-connect tears them down.
   */
  async join(contestId: string): Promise<ContestRoomResponse> {
    return new Promise((resolve, reject) => {
      // Ensure the singleton transport is connecting. connect() reuses a
      // connected client or spawns a fresh one when half-open.
      this.transport.connect();

      const performJoin = () => {
        // Unsubscribe from the previous contest subscription if any.
        this.transport.unsubscribeKey(`contest-${this.currentContestId}`);

        // Subscribe to the contest-specific topic and route by parsed event.
        this.transport.subscribe(
          `contest-${contestId}`,
          `/topic/contest/${contestId}`,
          (message: IMessage) => {
            try {
              const data = JSON.parse(message.body);
              const eventType = data.type || data.event || "contest_update";
              this.transport.dispatch(eventType, message);
            } catch {
              this.transport.dispatch("contest_update", message);
            }
          },
        );

        // Announce the join to the server.
        this.transport.publish("/app/contest.join", JSON.stringify({ contestId }));

        this.currentContestId = contestId;

        // STOMP gives no direct ack, so a join is assumed successful once the
        // subscription + publish have landed.
        resolve({
          success: true,
          contestId,
          message: `Successfully joined contest ${contestId}`,
        });
      };

      // Fast path: already connected — join synchronously, no pending hooks.
      if (this.transport.isConnected()) {
        performJoin();
        return;
      }

      // Still connecting: wait for the transport's one-shot "ready" event. We
      // never overwrite client.onConnect (the old override destroyed status
      // notification + the broadcast subscription, and the parallel
      // "connected_once" callback was dead code). The pending hook + timeout
      // are tracked so dispose (unmount) can tear them down.
      let settled = false;
      const cleanup = () => {
        settled = true;
        this.transport.off("ready", ready);
        clearTimeout(timeoutId);
        this.pendingJoinCleanup = null;
      };
      const ready = () => {
        if (settled) return;
        cleanup();
        performJoin();
      };
      const timeoutId = setTimeout(() => {
        if (settled) return;
        cleanup();
        reject(new Error("Connection timeout"));
      }, JOIN_TIMEOUT_MS);
      this.transport.on("ready", ready);
      this.pendingJoinCleanup = cleanup;
    });
  }

  /**
   * Leave the current contest room. No-op (success) when not in a room.
   */
  async leave(): Promise<ContestRoomResponse> {
    if (!this.currentContestId) {
      return {
        success: true,
        contestId: "",
        message: "Not in any contest room",
      };
    }

    const contestId = this.currentContestId;

    if (this.transport.isConnected()) {
      this.transport.unsubscribeKey(`contest-${contestId}`);
      this.transport.publish(
        "/app/contest.leave",
        JSON.stringify({ contestId }),
      );
    }

    this.currentContestId = null;

    return {
      success: true,
      contestId,
      message: `Successfully left contest ${contestId}`,
    };
  }

  // ─── Typed room events ──────────────────────────────────────────────

  onRankingUpdate(callback: RoomEventListener<RankingUpdatePayload>): () => void {
    return this.register(RANKING_UPDATE, callback);
  }

  onFirstSolve(callback: RoomEventListener<FirstSolvePayload>): () => void {
    return this.register(FIRST_SOLVE, callback);
  }

  onAnnouncement(callback: RoomEventListener<AnnouncementPayload>): () => void {
    return this.register(ANNOUNCEMENT, callback);
  }

  onContestStatus(
    callback: RoomEventListener<ContestStatusPayload>,
  ): () => void {
    return this.register(CONTEST_STATUS, callback);
  }

  onSubmissionResult(
    callback: RoomEventListener<SubmissionResultPayload>,
  ): () => void {
    return this.register(SUBMISSION_RESULT, callback);
  }

  onConnectionStatus(
    callback: (status: ConnectionStatus) => void,
  ): () => void {
    return this.register(CONNECTION_STATUS, callback);
  }

  private register<T>(event: string, callback: RoomEventListener<T>): () => void {
    const handler = callback as unknown as EventHandler;
    this.transport.on(event, handler);
    return () => this.transport.off(event, handler);
  }

  /**
   * Tear down any pending join-during-connect hooks. Safe to call when no
   * join is in flight. Does not disconnect the shared transport.
   */
  dispose(): void {
    this.pendingJoinCleanup?.();
    this.pendingJoinCleanup = null;
  }
}
