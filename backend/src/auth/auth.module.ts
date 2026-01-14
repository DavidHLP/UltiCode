import {
  forwardRef,
  Injectable,
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
import Redis from 'ioredis';

/**
 * Redis connection holder for cleanup on module destroy
 * This ensures the Redis connection is properly closed when the module is destroyed
 */
@Injectable()
class RedisConnectionHolder implements OnModuleDestroy {
  public readonly connection: Redis;

  constructor() {
    this.connection = new Redis({
      host: process.env.REDIS_HOST || 'localhost',
      port: parseInt(process.env.REDIS_PORT || '6379'),
      password: process.env.REDIS_PASSWORD || undefined,
      maxRetriesPerRequest: 3,
      retryStrategy: (times) => {
        const delay = Math.min(times * 50, 2000);
        return delay;
      },
    });
  }

  async onModuleDestroy() {
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
        return {
          secret,
          signOptions: { expiresIn: '7d' }, // Token 有效期 7 天
        };
      },
    }),
  ],
  providers: [
    AuthService,
    AuthGuard,
    TokenBlacklistService,
    PrismaService,
    RedisConnectionHolder,
    {
      provide: REDIS_CONNECTION,
      useExisting: RedisConnectionHolder,
    },
  ],
  controllers: [AuthController],
  exports: [AuthService, AuthGuard, TokenBlacklistService],
})
export class AuthModule {}
