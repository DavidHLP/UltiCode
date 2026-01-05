import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Response } from 'express';
import * as bcrypt from 'bcrypt';
import { UserService } from '../user/user.service';
import { RegisterDto } from './dto/register.dto';
import { BusinessException } from '../common/exceptions/business.exception';
import { ErrorCode } from '../common/error-codes';
import { TokenBlacklistService } from './token-blacklist.service';

export interface LoginResponse {
  access_token: string;
  user: {
    id: string;
    username: string;
    name: string;
    role: string;
  };
}

export interface LogoutDto {
  token?: string;
}

@Injectable()
export class AuthService {
  constructor(
    private userService: UserService,
    private jwtService: JwtService,
    private tokenBlacklistService: TokenBlacklistService,
  ) {}

  /**
   * 密码哈希函数 - 使用 bcrypt 进行安全哈希
   * @param password 明文密码
   * @returns bcrypt 哈希后的密码 (包含盐值和工作因子)
   */
  private async hashPassword(password: string): Promise<string> {
    // 使用 bcrypt 进行密码哈希，盐轮数为 10 (推荐范围: 10-12)
    // bcrypt 自动处理盐值生成和存储，每次哈希结果都不同但验证相同
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
   * 生成 JWT Token
   */
  private generateToken(
    userId: string,
    username: string,
    role: string,
  ): string {
    const payload = { sub: userId, username, role };
    return this.jwtService.sign(payload);
  }

  async signIn(username: string, password: string): Promise<LoginResponse> {
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

    // 生成 JWT token
    const token = this.generateToken(user.id, user.username, user.role);

    return {
      access_token: token,
      user: {
        id: user.id,
        username: user.username,
        name: user.name || user.username,
        role: user.role,
      },
    };
  }

  async logout(logoutDto: LogoutDto): Promise<{ message: string }> {
    // If a token is provided, add it to the blacklist
    if (logoutDto.token) {
      // Decode the token to get its remaining time
      try {
        // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
        const decoded = this.jwtService.decode(logoutDto.token);

        if (decoded && typeof decoded === 'object' && 'exp' in decoded) {
          // Calculate TTL until token expiration
          const now = Math.floor(Date.now() / 1000);
          // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
          const ttl = decoded.exp - now;

          // Only blacklist if token hasn't expired yet
          if (ttl > 0) {
            await this.tokenBlacklistService.addToBlacklist(
              logoutDto.token,
              ttl,
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

    return { message: 'Logged out successfully' };
  }

  async register(registerDto: RegisterDto): Promise<LoginResponse> {
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
    const id = `u-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;

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

    // 自动登录
    const token = this.generateToken(
      newUser.id,
      newUser.username,
      newUser.role,
    );

    return {
      access_token: token,
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
      return { messageKey: 'auth.forgotPassword.successMessage' };
    }

    // TODO: In production:
    // 1. Generate reset token
    // 2. Save to database with expiration
    // 3. Send email
    console.log(`[Mock Email] Password reset link sent to ${email}`);

    return { messageKey: 'auth.forgotPassword.successMessage' };
  }

  githubLogin(res: Response) {
    // GitHub OAuth 登录
    const clientId = process.env.GITHUB_CLIENT_ID || 'mock_client_id';
    const redirectUri =
      process.env.GITHUB_REDIRECT_URI ||
      'http://localhost:3000/auth/github/callback';
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
        id: `u-gh-${Date.now()}`,
        username: githubUser.username,
        email: githubUser.email,
        name: 'GitHub User',
        avatar: githubUser.avatar,
        // GitHub 登录不需要密码
      });
    }

    const token = this.generateToken(user.id, user.username, user.role);

    // 重定向到前端并携带 token
    const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:5173';
    res.redirect(`${frontendUrl}/login?token=${token}&userId=${user.id}`);
  }
}
