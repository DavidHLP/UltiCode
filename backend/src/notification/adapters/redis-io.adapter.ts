import { IoAdapter } from '@nestjs/platform-socket.io';
import { ServerOptions, Server as SocketIoServer } from 'socket.io';
import { createAdapter } from '@socket.io/redis-adapter';
import { ConfigService } from '@nestjs/config';
import { Redis } from 'ioredis';
import { INestApplicationContext, Logger } from '@nestjs/common';

export class RedisIoAdapter extends IoAdapter {
  private adapterConstructor: ReturnType<typeof createAdapter>;
  private readonly logger = new Logger(RedisIoAdapter.name);

  constructor(
    private app: INestApplicationContext,
    private configService: ConfigService,
  ) {
    super(app);
  }

  connectToRedis(): void {
    const host = this.configService.get<string>('REDIS_HOST', 'localhost');
    const port = this.configService.get<number>('REDIS_PORT', 6379);
    const password = this.configService.get<string>('REDIS_PASSWORD', '');

    const pubClient = new Redis({
      host,
      port,
      password: password || undefined,
    });

    const subClient = pubClient.duplicate();

    pubClient.on('error', (err) => {
      this.logger.error('Redis pub client error:', err);
    });

    subClient.on('error', (err) => {
      this.logger.error('Redis sub client error:', err);
    });

    this.adapterConstructor = createAdapter(pubClient, subClient);
  }

  createIOServer(port: number, options?: ServerOptions): unknown {
    const server = super.createIOServer(port, {
      ...options,
      cors: {
        origin: [
          'http://localhost:9002',
          'http://localhost:9003',
          this.configService.get<string>('FRONTEND_URL'),
          this.configService.get<string>('ADMIN_URL'),
        ].filter(Boolean),
        credentials: true,
      },
    }) as SocketIoServer;

    if (this.adapterConstructor) {
      server.adapter(this.adapterConstructor);
    }

    return server;
  }
}
