import {
  WebSocketGateway,
  WebSocketServer,
  SubscribeMessage,
  OnGatewayConnection,
  OnGatewayDisconnect,
  ConnectedSocket,
  MessageBody,
  WsException,
} from '@nestjs/websockets';
import { Logger, Inject, forwardRef } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Server, Socket } from 'socket.io';
import { TokenBlacklistService } from '../../auth/token-blacklist.service';
import { UserService } from '../../user/user.service';
import { isFeatureEnabled } from '../../common/config/feature-flags.config';

/**
 * JWT payload structure for WebSocket authentication
 */
interface JwtPayload {
  sub: string;
  username: string;
  role: string;
}

/**
 * Client data stored on socket
 */
interface SocketClientData {
  userId: string;
  username: string;
  role: string;
}

/**
 * Response for join/leave contest operations
 */
interface ContestRoomResponse {
  success: boolean;
  contestId: string;
  message: string;
  error?: string;
}

/**
 * Ranking update event payload
 */
export interface RankingUpdatePayload {
  contestId: string;
  rankings: Array<{
    rank: number;
    userId: string;
    username: string;
    score: number;
    solvedCount: number;
    penalty?: number;
  }>;
  updatedAt: Date;
}

/**
 * First solve notification payload
 */
export interface FirstSolvePayload {
  contestId: string;
  problemId: string;
  problemTitle: string;
  userId: string;
  username: string;
  solvedAt: Date;
}

/**
 * Announcement payload
 */
export interface AnnouncementPayload {
  id: string;
  contestId: string;
  title: string;
  content: string;
  createdAt: Date;
}

/**
 * Contest status update payload
 */
export interface ContestStatusPayload {
  contestId: string;
  status: 'upcoming' | 'registration' | 'running' | 'ended';
  startedAt?: Date;
  endsAt?: Date;
  message?: string;
}

/**
 * Submission result payload
 */
export interface SubmissionResultPayload {
  submissionId: string;
  contestId: string;
  problemId: string;
  userId: string;
  status: string;
  score: number;
  timeUsed?: number;
  memoryUsed?: number;
  judgedAt: Date;
}

/**
 * WebSocket Gateway for real-time contest events
 *
 * Provides real-time updates for:
 * - Ranking updates
 * - First solve notifications
 * - Contest announcements
 * - Contest status changes
 * - Submission results
 */
@WebSocketGateway({
  namespace: '/contest',
  cors: {
    origin: true,
    credentials: true,
  },
  transports: ['websocket', 'polling'],
})
export class ContestGateway
  implements OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  private server!: Server;

  private readonly logger = new Logger(ContestGateway.name);

  constructor(
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    @Inject(forwardRef(() => TokenBlacklistService))
    private readonly tokenBlacklistService: TokenBlacklistService,
    @Inject(forwardRef(() => UserService))
    private readonly userService: UserService,
  ) {}

  /**
   * Handle new WebSocket connection
   * Authenticates user via JWT token from auth or headers
   */
  async handleConnection(client: Socket): Promise<void> {
    try {
      const token = this.extractToken(client);

      if (!token) {
        this.logger.warn(`Connection rejected: No token provided`);
        client.disconnect(true);
        return;
      }

      // Check if token is blacklisted
      const isBlacklisted =
        await this.tokenBlacklistService.isBlacklisted(token);
      if (isBlacklisted) {
        this.logger.warn(`Connection rejected: Token is blacklisted`);
        client.disconnect(true);
        return;
      }

      // Verify token
      const payload = await this.jwtService.verifyAsync<JwtPayload>(token);

      if (!payload?.sub) {
        this.logger.warn(`Connection rejected: Invalid token payload`);
        client.disconnect(true);
        return;
      }

      // Verify user exists
      const user = await this.userService.findOne(payload.sub);
      if (!user) {
        this.logger.warn(`Connection rejected: User not found`);
        client.disconnect(true);
        return;
      }

      // Attach user data to socket
      client.data = {
        userId: payload.sub,
        username: payload.username,
        role: payload.role,
      } as SocketClientData;

      // Join user's personal room for direct messages
      await client.join(`user:${payload.sub}`);

      this.logger.debug(
        `Client connected: ${client.id} (User: ${payload.username})`,
      );
    } catch (error) {
      this.logger.error(`Connection error: ${(error as Error).message}`);
      client.disconnect(true);
    }
  }

  /**
   * Handle client disconnection
   */
  handleDisconnect(client: Socket): void {
    const userData = client.data as SocketClientData | undefined;
    if (userData?.username) {
      this.logger.debug(
        `Client disconnected: ${client.id} (User: ${userData.username})`,
      );
    } else {
      this.logger.debug(`Client disconnected: ${client.id}`);
    }
  }

  /**
   * Join a contest room to receive updates
   */
  @SubscribeMessage('join_contest')
  async handleJoinContest(
    @ConnectedSocket() client: Socket,
    @MessageBody() contestId: string,
  ): Promise<ContestRoomResponse> {
    const userData = client.data as SocketClientData | undefined;

    if (!userData?.userId) {
      throw new WsException({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'You must be authenticated to join a contest',
      });
    }

    const roomName = this.getContestRoomName(contestId);
    await client.join(roomName);

    this.logger.debug(`User ${userData.username} joined contest ${contestId}`);

    return {
      success: true,
      contestId,
      message: `Successfully joined contest ${contestId}`,
    };
  }

  /**
   * Leave a contest room
   */
  @SubscribeMessage('leave_contest')
  async handleLeaveContest(
    @ConnectedSocket() client: Socket,
    @MessageBody() contestId: string,
  ): Promise<ContestRoomResponse> {
    const userData = client.data as SocketClientData | undefined;

    if (!userData?.userId) {
      throw new WsException({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'You must be authenticated',
      });
    }

    const roomName = this.getContestRoomName(contestId);

    if (client.rooms.has(roomName)) {
      await client.leave(roomName);
      this.logger.debug(`User ${userData.username} left contest ${contestId}`);
      return {
        success: true,
        contestId,
        message: `Successfully left contest ${contestId}`,
      };
    }

    return {
      success: true,
      contestId,
      message: `You were not in contest ${contestId}`,
    };
  }

  // ==================== Event Emission Methods ====================

  /**
   * Emit ranking update to all clients in a contest room
   */
  emitRankingUpdate(contestId: string, data: RankingUpdatePayload): void {
    if (!isFeatureEnabled('ENABLE_REALTIME_RANKING')) {
      this.logger.debug(
        `Skipping ranking update: Feature disabled for contest ${contestId}`,
      );
      return;
    }

    const roomName = this.getContestRoomName(contestId);
    this.server.to(roomName).emit('ranking_update', {
      ...data,
      updatedAt: data.updatedAt || new Date(),
    });

    this.logger.debug(`Emitted ranking_update to ${roomName}`);
  }

  /**
   * Emit first solve notification
   */
  emitFirstSolve(contestId: string, data: FirstSolvePayload): void {
    if (!isFeatureEnabled('ENABLE_FIRST_SOLVE_NOTIFICATIONS')) {
      this.logger.debug(
        `Skipping first solve notification: Feature disabled for contest ${contestId}`,
      );
      return;
    }

    const roomName = this.getContestRoomName(contestId);
    this.server.to(roomName).emit('first_solve', data);

    this.logger.log(
      `First solve: User ${data.username} solved problem ${data.problemTitle} in contest ${contestId}`,
    );
  }

  /**
   * Emit announcement to contest room
   */
  emitAnnouncement(contestId: string, data: AnnouncementPayload): void {
    const roomName = this.getContestRoomName(contestId);
    this.server.to(roomName).emit('announcement', data);

    this.logger.log(`Announcement sent to contest ${contestId}: ${data.title}`);
  }

  /**
   * Emit contest status update
   */
  emitContestStatus(contestId: string, data: ContestStatusPayload): void {
    const roomName = this.getContestRoomName(contestId);
    this.server.to(roomName).emit('contest_status', data);

    this.logger.log(`Contest ${contestId} status changed to: ${data.status}`);
  }

  /**
   * Emit submission result to a specific user
   */
  emitSubmissionResult(userId: string, data: SubmissionResultPayload): void {
    const userRoom = this.getUserRoomName(userId);
    this.server.to(userRoom).emit('submission_result', data);

    this.logger.debug(
      `Submission result sent to user ${userId}: ${data.status}`,
    );
  }

  // ==================== Utility Methods ====================

  /**
   * Get total number of connected clients
   */
  getConnectionCount(): number {
    return this.server?.sockets?.sockets?.size ?? 0;
  }

  /**
   * Get number of clients in a specific contest room
   */
  getContestRoomSize(contestId: string): number {
    const roomName = this.getContestRoomName(contestId);
    const room = this.server?.sockets?.adapter?.rooms?.get(roomName);
    return room?.size ?? 0;
  }

  /**
   * Get list of contest rooms a user is in
   */
  getUserContestRooms(userId: string): string[] {
    const socket = Array.from(
      this.server?.sockets?.sockets?.values() ?? [],
    ).find((s) => (s.data as SocketClientData)?.userId === userId);

    if (!socket) {
      return [];
    }

    const contestRooms: string[] = [];
    socket.rooms.forEach((room) => {
      if (room.startsWith('contest:')) {
        contestRooms.push(room.replace('contest:', ''));
      }
    });

    return contestRooms;
  }

  /**
   * Broadcast to all connected clients
   */
  broadcastToAll(event: string, data: unknown): void {
    this.server.emit(event, data);
  }

  // ==================== Private Helper Methods ====================

  /**
   * Extract JWT token from socket handshake
   */
  private extractToken(client: Socket): string | null {
    // Try auth token first
    const authToken = client.handshake.auth?.token as string | undefined;
    if (authToken && typeof authToken === 'string') {
      return authToken;
    }

    // Try Authorization header
    const authHeader = client.handshake.headers?.authorization;
    if (authHeader?.startsWith('Bearer ')) {
      return authHeader.slice(7);
    }

    // Try query parameter (for fallback)
    const queryToken = client.handshake.query?.token;
    if (typeof queryToken === 'string') {
      return queryToken;
    }

    return null;
  }

  /**
   * Get contest room name from contest ID
   */
  private getContestRoomName(contestId: string): string {
    return `contest:${contestId}`;
  }

  /**
   * Get user room name from user ID
   */
  private getUserRoomName(userId: string): string {
    return `user:${userId}`;
  }
}
