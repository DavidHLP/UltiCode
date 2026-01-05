import { forwardRef, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AuthService } from './auth.service';
import { AuthController } from './auth.controller';
import { UserModule } from '../user/user.module';
import { JwtModule } from '@nestjs/jwt';
import { AuthGuard } from './auth.guard';
import { TokenBlacklistService } from './token-blacklist.service';
import { PrismaService } from '../prisma.service';

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
  providers: [AuthService, AuthGuard, TokenBlacklistService, PrismaService],
  controllers: [AuthController],
  exports: [AuthService, AuthGuard, TokenBlacklistService],
})
export class AuthModule {}
