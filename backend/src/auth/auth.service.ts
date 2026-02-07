import { Injectable, UnauthorizedException } from '@nestjs/common';
import { Response } from 'express';
import { UserService } from '../user/user.service';
import { PrismaService } from '../prisma.service';
import { RegisterDto } from './dto/register.dto';
import { BusinessException } from '../common/exceptions/business.exception';
import { ErrorCode } from '../common/error-codes';
import { TokenBlacklistService } from './token-blacklist.service';
import { CsrfService } from './csrf.service';
import { RefreshTokenService } from './refresh-token.service';
import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';
import { CookieService } from './services/cookie.service';
import { OAuthService } from './services/oauth.service';
import { randomUUID } from 'crypto';
import { Prisma } from '@prisma/client';

export interface LoginResponse {
  user: {
    id: string;
    username: string;
    name: string;
    role: string;
  };
}

export interface LoginResponseWithCookies extends LoginResponse {
  csrf_token?: string;
}

export interface LogoutDto {
  token?: string;
}

export interface ResetPasswordDto {
  token: string;
  newPassword: string;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly userService: UserService,
    private readonly tokenBlacklistService: TokenBlacklistService,
    private readonly csrfService: CsrfService,
    private readonly refreshTokenService: RefreshTokenService,
    private readonly prisma: PrismaService,
    private readonly passwordService: PasswordService,
    private readonly tokenService: TokenService,
    private readonly cookieService: CookieService,
    private readonly oauthService: OAuthService,
  ) {}

  async signIn(
    username: string,
    password: string,
    res: Response,
  ): Promise<LoginResponseWithCookies> {
    const user = await this.passwordService.verifyCredentials(
      username,
      password,
    );

    const accessToken = this.tokenService.generateAccessToken(
      user.id,
      user.username,
      user.role,
    );

    const refreshTokenRecord =
      await this.refreshTokenService.createRefreshToken(user.id);

    const csrfToken = await this.csrfService.generateCsrfToken(user.id);

    this.cookieService.setAuthCookies(
      res,
      accessToken,
      refreshTokenRecord.token,
    );

    return {
      csrf_token: csrfToken,
      user: {
        id: user.id,
        username: user.username,
        name: user.name || user.username,
        role: user.role,
      },
    };
  }

  async logout(
    logoutDto: LogoutDto,
    res: Response,
  ): Promise<{ message: string }> {
    if (logoutDto.token) {
      try {
        const ttl = this.tokenService.getTokenExpiry(logoutDto.token);

        if (ttl && ttl > 0) {
          await this.tokenBlacklistService.addToBlacklist(logoutDto.token, ttl);
        }

        const userId = this.tokenService.getUserIdFromToken(logoutDto.token);
        if (userId) {
          await this.csrfService.revokeCsrfToken(userId);
          await this.refreshTokenService.revokeAllUserTokens(userId);
        }
      } catch {
        await this.tokenBlacklistService.addToBlacklist(logoutDto.token);
      }
    }

    this.cookieService.clearAuthCookies(res);

    return { message: 'Logged out successfully' };
  }

  async refreshTokens(
    refreshToken: string,
    res: Response,
  ): Promise<LoginResponse> {
    const tokenRecord =
      await this.refreshTokenService.rotateRefreshToken(refreshToken);

    if (!tokenRecord) {
      this.cookieService.clearAuthCookies(res);
      throw new UnauthorizedException('Invalid or expired refresh token');
    }

    const user = await this.userService.findOne(tokenRecord.user_id);
    if (!user) {
      throw new UnauthorizedException('User not found');
    }

    const accessToken = this.tokenService.generateAccessToken(
      user.id,
      user.username,
      user.role,
    );

    this.cookieService.setAuthCookies(res, accessToken, tokenRecord.token);

    return {
      user: {
        id: user.id,
        username: user.username,
        name: user.name || user.username,
        role: user.role,
      },
    };
  }

  async register(
    registerDto: RegisterDto,
    res: Response,
  ): Promise<LoginResponseWithCookies> {
    const existingUser = await this.userService.findByUsername(
      registerDto.username,
    );
    if (existingUser) {
      throw new BusinessException(ErrorCode.AUTH_USERNAME_TAKEN);
    }

    const existingEmail = await this.userService.findByEmail(registerDto.email);
    if (existingEmail) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
    }

    const id = randomUUID();

    const hashedPassword = await this.passwordService.hashPassword(
      registerDto.password,
    );

    const fallbackAvatar = `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(
      registerDto.username,
    )}`;

    // Use interactive transaction for atomic user + refresh token creation
    const result = await this.prisma.$transaction(
      async (tx: Prisma.TransactionClient) => {
        // Create user within transaction
        const newUser = await tx.user.create({
          data: {
            id,
            username: registerDto.username,
            email: registerDto.email,
            name: registerDto.username,
            avatar: registerDto.avatar || fallbackAvatar,
            password: hashedPassword,
          },
        });

        // Create refresh token within transaction
        const refreshTokenRecord = await tx.refreshToken.create({
          data: {
            user_id: newUser.id,
            token: this.refreshTokenService.generateToken(),
            expires_at: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
          },
        });

        return { newUser, refreshTokenRecord };
      },
    );

    const accessToken = this.tokenService.generateAccessToken(
      result.newUser.id,
      result.newUser.username,
      result.newUser.role,
    );

    const csrfToken = await this.csrfService.generateCsrfToken(
      result.newUser.id,
    );

    this.cookieService.setAuthCookies(
      res,
      accessToken,
      result.refreshTokenRecord.token,
    );

    return {
      csrf_token: csrfToken,
      user: {
        id: result.newUser.id,
        username: result.newUser.username,
        name: result.newUser.name || result.newUser.username,
        role: result.newUser.role,
      },
    };
  }

  async forgotPassword(email: string) {
    return this.passwordService.forgotPassword(email);
  }

  async resetPassword(resetPasswordDto: ResetPasswordDto) {
    return this.passwordService.resetPassword(
      resetPasswordDto.token,
      resetPasswordDto.newPassword,
    );
  }

  githubLogin(res: Response) {
    return this.oauthService.githubLogin(res);
  }

  async githubCallback(code: string, res: Response) {
    return this.oauthService.githubCallback(code, res);
  }

  getUserPermissions(role: string): string[] {
    const perms = new Set<string>();

    if (role === 'SUPER_ADMIN') {
      perms.add('*:*');
    } else if (role === 'ADMIN') {
      perms.add('READ:USER');
      perms.add('CREATE:USER');
      perms.add('UPDATE:USER');
      perms.add('DELETE:USER');
      perms.add('MODERATE:USER');
      perms.add('READ:PROBLEM');
      perms.add('CREATE:PROBLEM');
      perms.add('UPDATE:PROBLEM');
      perms.add('DELETE:PROBLEM');
      perms.add('PUBLISH:PROBLEM');
      perms.add('READ:CONTEST');
      perms.add('CREATE:CONTEST');
      perms.add('UPDATE:CONTEST');
      perms.add('DELETE:CONTEST');
      perms.add('READ:SOLUTION');
      perms.add('MODERATE:SOLUTION');
      perms.add('READ:FORUM_POST');
      perms.add('MODERATE:FORUM_POST');
      perms.add('READ:FORUM_COMMENT');
      perms.add('MODERATE:FORUM_COMMENT');
      perms.add('READ:SYSTEM');
    } else if (role === 'MODERATOR') {
      perms.add('READ:USER');
      perms.add('READ:PROBLEM');
      perms.add('READ:CONTEST');
      perms.add('READ:SOLUTION');
      perms.add('MODERATE:SOLUTION');
      perms.add('UPDATE:SOLUTION');
      perms.add('DELETE:SOLUTION');
      perms.add('READ:FORUM_POST');
      perms.add('MODERATE:FORUM_POST');
      perms.add('UPDATE:FORUM_POST');
      perms.add('DELETE:FORUM_POST');
      perms.add('READ:FORUM_COMMENT');
      perms.add('MODERATE:FORUM_COMMENT');
      perms.add('UPDATE:FORUM_COMMENT');
      perms.add('DELETE:FORUM_COMMENT');
      perms.add('READ:SYSTEM');
    }

    return Array.from(perms);
  }
}
