import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Response } from 'express';
import * as bcrypt from 'bcrypt';
import { randomBytes, randomUUID } from 'crypto';
import { UserService } from '../user/user.service';
import { PrismaService } from '../prisma.service';
import { RegisterDto } from './dto/register.dto';
import { BusinessException } from '../common/exceptions/business.exception';
import { ErrorCode } from '../common/error-codes';
import { TokenBlacklistService } from './token-blacklist.service';
import { CsrfService } from './csrf.service';
import { RefreshTokenService } from './refresh-token.service';

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
  // Password reset token expires in 1 hour
  private readonly PASSWORD_RESET_EXPIRY = 60 * 60 * 1000; // 1 hour in ms

  // Access token expiry (15 minutes) - matches JWT_ACCESS_EXPIRY
  private readonly ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000;

  constructor(
    private userService: UserService,
    private jwtService: JwtService,
    private tokenBlacklistService: TokenBlacklistService,
    private csrfService: CsrfService,
    private refreshTokenService: RefreshTokenService,
    private prisma: PrismaService,
  ) {}

  /**
   * 密码哈希函数 - 使用 bcrypt 进行安全哈希
   * @param password 明文密码
   * @returns bcrypt 哈希后的密码 (包含盐值和工作因子)
   */
  private async hashPassword(password: string): Promise<string> {
    const saltRounds = 10;
    return bcrypt.hash(password, saltRounds);
  }

  /**
   * 验证密码 - 使用 bcrypt 的 compare 方法
   * @param password 明文密码
   * @param hashedPassword bcrypt 哈希后的密码
   * @returns 密码是否匹配
   */
  private async verifyPassword(
    password: string,
    hashedPassword: string,
  ): Promise<boolean> {
    return bcrypt.compare(password, hashedPassword);
  }

  /**
   * 生成 JWT Token (Access Token - short lived)
   */
  private generateAccessToken(
    userId: string,
    username: string,
    role: string,
  ): string {
    const payload = { sub: userId, username, role };
    return this.jwtService.sign(payload);
  }

  /**
   * Set auth cookies (access_token and refresh_token)
   */
  private setAuthCookies(
    res: Response,
    accessToken: string,
    refreshToken: string,
  ): void {
    const isSecure = process.env.COOKIE_SECURE === 'true';
    const sameSite = (process.env.COOKIE_SAME_SITE || 'lax') as
      | 'strict'
      | 'lax'
      | 'none';
    const domain = process.env.COOKIE_DOMAIN;

    // Access token cookie (short-lived - 15 minutes)
    res.cookie('access_token', accessToken, {
      httpOnly: true,
      secure: isSecure,
      sameSite,
      domain,
      maxAge: this.ACCESS_TOKEN_EXPIRY,
    });

    // Refresh token cookie (long-lived - 7 days)
    res.cookie('refresh_token', refreshToken, {
      httpOnly: true,
      secure: isSecure,
      sameSite,
      domain,
      maxAge: 7 * 24 * 60 * 60 * 1000, // 7 days
    });
  }

  /**
   * Clear auth cookies
   */
  private clearAuthCookies(res: Response): void {
    const domain = process.env.COOKIE_DOMAIN;

    res.clearCookie('access_token', { domain });
    res.clearCookie('refresh_token', { domain });
  }

  async signIn(
    username: string,
    password: string,
    res: Response,
  ): Promise<LoginResponseWithCookies> {
    // 查找用户
    const user = await this.userService.findByUsername(username);

    if (!user) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    // 验证密码
    if (!user.password) {
      throw new BusinessException(ErrorCode.AUTH_NO_PASSWORD);
    }

    const isPasswordValid = await this.verifyPassword(password, user.password);
    if (!isPasswordValid) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    // 生成 JWT access token (short-lived)
    const accessToken = this.generateAccessToken(
      user.id,
      user.username,
      user.role,
    );

    // 生成 refresh token (long-lived)
    const refreshTokenRecord =
      await this.refreshTokenService.createRefreshToken(user.id);

    // 生成 CSRF token
    const csrfToken = await this.csrfService.generateCsrfToken(user.id);

    // Set httpOnly cookies
    this.setAuthCookies(res, accessToken, refreshTokenRecord.token);

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

  /**
   * Decode JWT token with proper type safety
   * @param token JWT token string
   * @returns Decoded token payload or null/string if invalid
   */
  private decodeToken(
    token: string,
  ): { exp: number; [key: string]: unknown } | null | string {
    return this.jwtService.decode(token);
  }

  async logout(
    logoutDto: LogoutDto,
    res: Response,
  ): Promise<{ message: string }> {
    // If a token is provided, add it to the blacklist
    if (logoutDto.token) {
      // Decode the token to get its remaining time
      try {
        const decoded = this.decodeToken(logoutDto.token);

        if (decoded && typeof decoded === 'object' && 'exp' in decoded) {
          // Calculate TTL until token expiration
          const now = Math.floor(Date.now() / 1000);
          const ttl = decoded.exp - now;

          // Only blacklist if token hasn't expired yet
          if (ttl > 0) {
            await this.tokenBlacklistService.addToBlacklist(
              logoutDto.token,
              ttl,
            );
          }

          // Revoke CSRF token
          if ('sub' in decoded) {
            await this.csrfService.revokeCsrfToken(decoded.sub as string);
            // Revoke all refresh tokens for this user
            await this.refreshTokenService.revokeAllUserTokens(
              decoded.sub as string,
            );
          }
        } else {
          // If we can't decode the token, use default TTL
          await this.tokenBlacklistService.addToBlacklist(logoutDto.token);
        }
      } catch {
        // If token is invalid, still try to blacklist it with default TTL
        await this.tokenBlacklistService.addToBlacklist(logoutDto.token);
      }
    }

    // Clear auth cookies
    this.clearAuthCookies(res);

    return { message: 'Logged out successfully' };
  }

  /**
   * Refresh tokens using a valid refresh token
   * Creates a new access token and rotates the refresh token
   */
  async refreshTokens(
    refreshToken: string,
    res: Response,
  ): Promise<LoginResponse> {
    const tokenRecord =
      await this.refreshTokenService.rotateRefreshToken(refreshToken);

    if (!tokenRecord) {
      this.clearAuthCookies(res);
      throw new UnauthorizedException('Invalid or expired refresh token');
    }

    const user = await this.userService.findOne(tokenRecord.user_id);
    if (!user) {
      throw new UnauthorizedException('User not found');
    }

    // Generate new access token
    const accessToken = this.generateAccessToken(
      user.id,
      user.username,
      user.role,
    );

    // Set new cookies
    this.setAuthCookies(res, accessToken, tokenRecord.token);

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
    // 检查用户名是否已存在
    const existingUser = await this.userService.findByUsername(
      registerDto.username,
    );
    if (existingUser) {
      throw new BusinessException(ErrorCode.AUTH_USERNAME_TAKEN);
    }

    // 检查邮箱是否已存在
    const existingEmail = await this.userService.findByEmail(registerDto.email);
    if (existingEmail) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_TAKEN);
    }

    // 生成用户 ID
    const id = randomUUID();

    // 哈希密码
    const hashedPassword = await this.hashPassword(registerDto.password);

    // 创建用户
    const fallbackAvatar = `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(
      registerDto.username,
    )}`;
    const newUser = await this.userService.create({
      id,
      username: registerDto.username,
      email: registerDto.email,
      name: registerDto.username,
      avatar: registerDto.avatar || fallbackAvatar,
      password: hashedPassword,
    });

    // 自动登录 - 生成 access token
    const accessToken = this.generateAccessToken(
      newUser.id,
      newUser.username,
      newUser.role,
    );

    // 生成 refresh token
    const refreshTokenRecord =
      await this.refreshTokenService.createRefreshToken(newUser.id);

    // 生成 CSRF token
    const csrfToken = await this.csrfService.generateCsrfToken(newUser.id);

    // Set httpOnly cookies
    this.setAuthCookies(res, accessToken, refreshTokenRecord.token);

    return {
      csrf_token: csrfToken,
      user: {
        id: newUser.id,
        username: newUser.username,
        name: newUser.name || newUser.username,
        role: newUser.role,
      },
    };
  }

  async forgotPassword(email: string) {
    const user = await this.userService.findByEmail(email);
    if (!user) {
      // Don't reveal whether user exists - always return same message
      return {
        message:
          'If an account exists with this email, a password reset link will be sent',
      };
    }

    // Invalidate any existing reset tokens for this user
    await this.prisma.passwordReset.updateMany({
      where: { user_id: user.id },
      data: { used_at: new Date() },
    });

    // Generate a secure random token
    const token = randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + this.PASSWORD_RESET_EXPIRY);

    // Save to database
    await this.prisma.passwordReset.create({
      data: {
        user_id: user.id,
        token,
        expires_at: expiresAt,
      },
    });

    // TODO: Send email with reset link containing the token
    // In production, this would send an email with a link like:
    // https://example.com/reset-password?token={token}

    return {
      message:
        'If an account exists with this email, a password reset link will be sent',
    };
  }

  async resetPassword(resetPasswordDto: ResetPasswordDto) {
    const { token, newPassword } = resetPasswordDto;

    // Find valid reset token
    const resetRecord = await this.prisma.passwordReset.findUnique({
      where: { token },
    });

    if (!resetRecord) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN);
    }

    // Check if token has been used
    if (resetRecord.used_at) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_ALREADY_USED);
    }

    // Check if token has expired
    if (new Date() > resetRecord.expires_at) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_EXPIRED);
    }

    // Hash new password
    const hashedPassword = await this.hashPassword(newPassword);

    // Update user password
    await this.userService.update(resetRecord.user_id, {
      password: hashedPassword,
    });

    // Mark token as used
    await this.prisma.passwordReset.update({
      where: { id: resetRecord.id },
      data: { used_at: new Date() },
    });

    // Invalidate all other reset tokens for this user
    await this.prisma.passwordReset.updateMany({
      where: {
        user_id: resetRecord.user_id,
        id: { not: resetRecord.id },
      },
      data: { used_at: new Date() },
    });

    return { message: 'Password has been reset successfully' };
  }

  githubLogin(res: Response) {
    // GitHub OAuth 登录
    const clientId = process.env.GITHUB_CLIENT_ID || 'mock_client_id';
    const redirectUri =
      process.env.GITHUB_REDIRECT_URI ||
      'http://localhost:4001/auth/github/callback';
    const target = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&scope=user:email`;
    res.redirect(target);
  }

  async githubCallback(_code: string, res: Response) {
    // TODO: 实际应用中应该：
    // 1. 使用 code 交换 access_token
    // 2. 使用 token 获取 GitHub 用户信息
    // 3. 在数据库中查找或创建用户

    // 模拟 GitHub 登录成功
    const githubUser = {
      username: 'github_user',
      email: 'github@example.com',
      avatar: 'https://avatars.githubusercontent.com/github_user?v=4',
    };

    let user = await this.userService.findByEmail(githubUser.email);
    if (!user) {
      user = await this.userService.create({
        id: randomUUID(),
        username: githubUser.username,
        email: githubUser.email,
        name: 'GitHub User',
        avatar: githubUser.avatar,
        // GitHub 登录不需要密码
      });
    }

    // Generate access token
    const accessToken = this.generateAccessToken(
      user.id,
      user.username,
      user.role,
    );

    // Generate refresh token
    const refreshTokenRecord =
      await this.refreshTokenService.createRefreshToken(user.id);

    // Generate CSRF token
    const csrfToken = await this.csrfService.generateCsrfToken(user.id);

    // Set httpOnly cookies
    this.setAuthCookies(res, accessToken, refreshTokenRecord.token);

    // 重定向到前端（不再在 URL 中携带 token）
    const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:5173';
    res.redirect(`${frontendUrl}/?csrf=${csrfToken}`);
  }

  /**
   * Get user permissions based on role
   */
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
