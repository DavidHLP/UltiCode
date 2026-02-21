import {
  WebSocketGateway,
  WebSocketServer,
  SubscribeMessage,
  OnGatewayConnection,
  OnGatewayDisconnect,
  ConnectedSocket,
  MessageBody,
} from '@nestjs/websockets';
import { Logger } from '@nestjs/common';
import { Server, Socket } from 'socket.io';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import {
  NotificationEvent,
  SubmissionResultPayload,
  ContestUpdatePayload,
  BadgeEarnedPayload,
  NotificationPayload,
} from './notification.events';

interface AuthenticatedSocket extends Socket {
  userId: string;
}

@WebSocketGateway({
  cors: {
    origin: [
      'http://localhost:9002',
      'http://localhost:9003',
      process.env.FRONTEND_URL,
      process.env.ADMIN_URL,
    ].filter(Boolean),
    credentials: true,
  },
  namespace: '/notifications',
})
export class NotificationGateway
  implements OnGatewayConnection, OnGatewayDisconnect
{
  @WebSocketServer()
  server: Server;

  private readonly logger = new Logger(NotificationGateway.name);
  private userSockets = new Map<string, Set<string>>();

  constructor(
    private configService: ConfigService,
    private jwtService: JwtService,
  ) {}

  async handleConnection(client: AuthenticatedSocket): Promise<void> {
    try {
      // Extract token from handshake auth or cookies
      const token =
        client.handshake.auth?.token ||
        this.extractTokenFromCookie(client);

      if (!token) {
        this.logger.warn(`Client ${client.id} connected without token`);
        client.disconnect();
        return;
      }

      // Verify JWT token
      const payload = await this.jwtService.verifyAsync(token);
      client.userId = payload.sub;

      // Track user's sockets
      if (!this.userSockets.has(client.userId)) {
        this.userSockets.set(client.userId, new Set());
      }
      this.userSockets.get(client.userId)!.add(client.id);

      // Join user's personal room
      client.join(`user:${client.userId}`);

      this.logger.log(
        `Client ${client.id} connected for user ${client.userId}`,
      );

      // Send connection confirmation
      client.emit('connected', {
        message: 'Successfully connected to notification service',
        userId: client.userId,
      });
    } catch (error) {
      this.logger.error(`Authentication failed for client ${client.id}`);
      client.disconnect();
    }
  }

  handleDisconnect(client: AuthenticatedSocket): void {
    if (client.userId && this.userSockets.has(client.userId)) {
      this.userSockets.get(client.userId)!.delete(client.id);

      if (this.userSockets.get(client.userId)!.size === 0) {
        this.userSockets.delete(client.userId);
      }
    }

    this.logger.log(`Client ${client.id} disconnected`);
  }

  private extractTokenFromCookie(client: Socket): string | null {
    const cookie = client.handshake.headers.cookie;
    if (!cookie) return null;

    const tokenMatch = cookie.match(/access_token=([^;]+)/);
    return tokenMatch ? tokenMatch[1] : null;
  }

  // Send notification to a specific user
  sendToUser(
    userId: string,
    event: NotificationEvent,
    data: unknown,
  ): boolean {
    const room = `user:${userId}`;
    this.server.to(room).emit(event, {
      data,
      timestamp: Date.now(),
    });
    return this.userSockets.has(userId);
  }

  // Send notification to multiple users
  sendToUsers(
    userIds: string[],
    event: NotificationEvent,
    data: unknown,
  ): void {
    userIds.forEach((userId) => this.sendToUser(userId, event, data));
  }

  // Broadcast to all connected clients
  broadcast(event: NotificationEvent, data: unknown): void {
    this.server.emit(event, {
      data,
      timestamp: Date.now(),
    });
  }

  // Send submission result
  sendSubmissionResult(userId: string, payload: SubmissionResultPayload): void {
    this.sendToUser(userId, NotificationEvent.SUBMISSION_RESULT, payload);
  }

  // Send contest update
  sendContestUpdate(
    contestId: string,
    userIds: string[],
    payload: ContestUpdatePayload,
  ): void {
    this.server
      .to(userIds.map((id) => `user:${id}`))
      .emit(NotificationEvent.CONTEST_UPDATE, {
        ...payload,
        contestId,
        timestamp: Date.now(),
      });
  }

  // Send badge earned notification
  sendBadgeEarned(userId: string, payload: BadgeEarnedPayload): void {
    this.sendToUser(userId, NotificationEvent.BADGE_EARNED, payload);
  }

  // Send generic notification
  sendNotification(userId: string, payload: NotificationPayload): void {
    this.sendToUser(userId, NotificationEvent.SYSTEM_ANNOUNCEMENT, payload);
  }

  // Get online users count
  getOnlineUsersCount(): number {
    return this.userSockets.size;
  }

  // Check if user is online
  isUserOnline(userId: string): boolean {
    return this.userSockets.has(userId);
  }

  // Subscribe to contest updates
  @SubscribeMessage('subscribe:contest')
  handleSubscribeContest(
    @ConnectedSocket() client: AuthenticatedSocket,
    @MessageBody() contestId: string,
  ): void {
    client.join(`contest:${contestId}`);
    this.logger.log(
      `User ${client.userId} subscribed to contest ${contestId}`,
    );
  }

  // Unsubscribe from contest updates
  @SubscribeMessage('unsubscribe:contest')
  handleUnsubscribeContest(
    @ConnectedSocket() client: AuthenticatedSocket,
    @MessageBody() contestId: string,
  ): void {
    client.leave(`contest:${contestId}`);
    this.logger.log(
      `User ${client.userId} unsubscribed from contest ${contestId}`,
    );
  }

  // Get connection status
  @SubscribeMessage('ping')
  handlePing(@ConnectedSocket() client: AuthenticatedSocket): void {
    client.emit('pong', { timestamp: Date.now() });
  }
}
