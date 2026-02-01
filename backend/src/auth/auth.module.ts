import {
  forwardRef,
  Injectable,
  Logger,
  Module,
  OnModuleDestroy,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { UserModule } from '../user/user.module';
import { JwtModule } from '@nestjs/jwt';
import { AuthGuard } from './auth.guard';
import {
  TokenBlacklistService,
  REDIS_CONNECTION,
} from './token-blacklist.service';
import { PrismaService } from '../prisma.service';
import { CsrfService } from './csrf.service';
import { CsrfGuard } from './csrf.guard';
import { RefreshTokenService } from './refresh-token.service';
import Redis from 'ioredis';

/**
 * Redis connection holder for cleanup on module destroy
 * This ensures the Redis connection is properly closed when the module is destroyed
 */
@Injectable()
class RedisConnectionHolder implements OnModuleDestroy {
  private readonly logger = new Logger(RedisConnectionHolder.name);
  public readonly connection: Redis;

  constructor() {
    const redisHost = process.env.REDIS_HOST || 'localhost';
    const redisPort = parseInt(process.env.REDIS_PORT || '6379');
    const redisPassword = process.env.REDIS_PASSWORD || '123456';

    this.logger.log(
      `[REDIS_CONNECT] Connecting to Redis at ${redisHost}:${redisPort}`,
    );

    this.connection = new Redis({
      host: redisHost,
      port: redisPort,
      password: redisPassword,
      maxRetriesPerRequest: 3,
      retryStrategy: (times) => {
        const delay = Math.min(times * 50, 2000);
        this.logger.warn(
          `[REDIS_RETRY] Retry attempt ${times}, delay=${delay}ms`,
        );
        return delay;
      },
    });

    // Log connection events
    this.connection.on('connect', () => {
      this.logger.log(`[REDIS_CONNECT] Connection established`);
    });

    this.connection.on('ready', () => {
      this.logger.log(`[REDIS_READY] Redis connection ready for commands`);
    });

    this.connection.on('error', (err: Error) => {
      this.logger.error(`[REDIS_ERROR] ${err.message}`, err.stack);
    });

    this.connection.on('close', () => {
      this.logger.log(`[REDIS_CLOSE] Connection closed`);
    });

    this.connection.on('reconnecting', () => {
      this.logger.warn(`[REDIS_RECONNECTING] Attempting to reconnect...`);
    });
  }

  async onModuleDestroy(): Promise<void> {
    this.logger.log(`[REDIS_DESTROY] Closing Redis connection`);
    await this.connection.quit();
  }
}

@Module({
  imports: [
    forwardRef(() => UserModule),
    JwtModule.registerAsync({
      global: true,
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => {
        const secret = configService.get<string>('JWT_SECRET');
        if (!secret || secret.length < 32) {
          throw new Error(
            'JWT_SECRET must be set in environment variables and be at least 32 characters long',
          );
        }
        const expiresIn = configService.get<string>('JWT_ACCESS_EXPIRY', '15m');
        return {
          secret,
          signOptions: {
            expiresIn: expiresIn as never,
          },
        };
      },
    }),
  ],
  providers: [
    AuthService,
    AuthGuard,
    TokenBlacklistService,
    CsrfService,
    CsrfGuard,
    RefreshTokenService,
    PrismaService,
    RedisConnectionHolder,
    {
      provide: REDIS_CONNECTION,
      useFactory: (holder: RedisConnectionHolder) => holder.connection,
      inject: [RedisConnectionHolder],
    },
  ],
  controllers: [AuthController],
  exports: [
    AuthService,
    AuthGuard,
    TokenBlacklistService,
    CsrfService,
    CsrfGuard,
    RefreshTokenService,
  ],
})
export class AuthModule {}
