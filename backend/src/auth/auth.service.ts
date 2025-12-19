import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { Response } from 'express';
import * as crypto from 'crypto';
import { UserService } from '../user/user.service';
import { RegisterDto } from './dto/register.dto';

export interface LoginResponse {
  access_token: string;
  user: {
    id: string;
    username: string;
    name: string;
  };
}

@Injectable()
export class AuthService {
  constructor(
    private userService: UserService,
    private jwtService: JwtService,
  ) {}

  /**
   * 密码哈希函数
   */
  private hashPassword(password: string): string {
    return crypto.createHash('sha256').update(password).digest('hex');
  }

  /**
   * 验证密码
   */
  private verifyPassword(password: string, hashedPassword: string): boolean {
    return this.hashPassword(password) === hashedPassword;
  }

  /**
   * 生成 JWT Token
   */
  private generateToken(userId: string, username: string): string {
    const payload = { sub: userId, username };
    return this.jwtService.sign(payload);
  }

  async signIn(username: string, password: string): Promise<LoginResponse> {
    // 查找用户
    const user = await this.userService.findByUsername(username);

    if (!user) {
      throw new UnauthorizedException('用户名或密码错误');
    }

    // 验证密码
    if (!user.password) {
      throw new UnauthorizedException('该账户未设置密码，请使用其他登录方式');
    }

    const isPasswordValid = this.verifyPassword(password, user.password);
    if (!isPasswordValid) {
      throw new UnauthorizedException('用户名或密码错误');
    }

    // 生成 JWT token
    const token = this.generateToken(user.id, user.username);

    return {
      access_token: token,
      user: {
        id: user.id,
        username: user.username,
        name: user.name || user.username,
      },
    };
  }

  logout() {
    return { message: 'Logged out successfully' };
  }

  async register(registerDto: RegisterDto): Promise<LoginResponse> {
    // 检查用户名是否已存在
    const existingUser = await this.userService.findByUsername(
      registerDto.username,
    );
    if (existingUser) {
      throw new UnauthorizedException('用户名已被使用');
    }

    // 检查邮箱是否已存在
    const existingEmail = await this.userService.findByEmail(registerDto.email);
    if (existingEmail) {
      throw new UnauthorizedException('邮箱已被使用');
    }

    // 生成用户 ID
    const id = `u-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;

    // 哈希密码
    const hashedPassword = this.hashPassword(registerDto.password);

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
    const token = this.generateToken(newUser.id, newUser.username);

    return {
      access_token: token,
      user: {
        id: newUser.id,
        username: newUser.username,
        name: newUser.name || newUser.username,
      },
    };
  }

  async forgotPassword(email: string) {
    const user = await this.userService.findByEmail(email);
    if (!user) {
      // 不泄露用户是否存在
      return { message: '如果该邮箱存在，重置链接已发送' };
    }

    // TODO: 实际应用中应该：
    // 1. 生成重置 token
    // 2. 保存到数据库并设置过期时间
    // 3. 发送邮件
    console.log(`[模拟邮件] 密码重置链接已发送到 ${email}`);

    return { message: '如果该邮箱存在，重置链接已发送' };
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

    const token = this.generateToken(user.id, user.username);

    // 重定向到前端并携带 token
    const frontendUrl = process.env.FRONTEND_URL || 'http://localhost:5173';
    res.redirect(`${frontendUrl}/login?token=${token}&userId=${user.id}`);
  }
}
