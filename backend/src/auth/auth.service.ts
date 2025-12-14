import { Injectable, UnauthorizedException } from '@nestjs/common';
import { Response } from 'express';
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
  constructor(private userService: UserService) {}

  async signIn(username: string, _pass: string): Promise<LoginResponse> {
    const user = await this.userService.findByUsername(username);

    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // For demo purposes, we accept any password since none is stored.
    // We generate a simple basic token (in production use JWT).
    // The token payload is just base64 of username:id
    const payload = Buffer.from(`${user.username}:${user.id}`).toString(
      'base64',
    );

    return {
      access_token: payload,
      user: {
        id: user.id,
        username: user.username,
        name: user.name,
      },
    };
  }

  logout() {
    return { message: 'Logged out successfully' };
  }

  async register(registerDto: RegisterDto): Promise<LoginResponse> {
    // Check if user exists
    const existingUser = await this.userService.findByUsername(
      registerDto.username,
    );
    if (existingUser) {
      throw new UnauthorizedException('Username already exists');
    }
    const existingEmail = await this.userService.findByEmail(registerDto.email);
    if (existingEmail) {
      throw new UnauthorizedException('Email already exists');
    }

    // Create user
    // Generate an ID (usually DB does this but our seed uses u-00X string format, so let's mock it or use uuid if not strict)
    // For this codebase, let's generate a quick ID.
    const id = `u-${Date.now()}`;

    // In real app, hash password here.

    const newUser = await this.userService.create({
      id,
      username: registerDto.username,
      email: registerDto.email,
      name: registerDto.username,
      avatar: registerDto.avatar || 'https://github.com/shadcn.png', // Default avatar
    });

    // Auto-login
    return this.signIn(newUser.username, '');
  }

  async forgotPassword(email: string) {
    const user = await this.userService.findByEmail(email);
    if (!user) {
      // Don't reveal user existence
      return { message: 'If the email exists, a reset link has been sent.' };
    }

    // Mock sending email
    console.log(`[Mock Email] Password reset link sent to ${email}`);

    return { message: 'If the email exists, a reset link has been sent.' };
  }

  githubLogin(res: Response) {
    // Direct redirect to GitHub OAuth
    // NOTE: In a real app, use environment variables for Client ID
    const clientId = process.env.GITHUB_CLIENT_ID || 'mock_client_id';
    const redirectUri = 'http://localhost:3000/auth/github/callback';
    const target = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&scope=user:email`;
    res.redirect(target);
  }

  async githubCallback(_code: string, res: Response) {
    // 1. Exchange code for token (Mocked here since we don't have valid ID/Secret)
    // 2. Fetch user profile from GitHub
    // 3. Find or Create User in DB

    // Mocking a successful GitHub login for "github_user"
    const githubUser = {
      username: 'github_user',
      email: 'github@example.com',
      avatar: 'https://github.com/shadcn.png',
    };

    let user = await this.userService.findByEmail(githubUser.email);
    if (!user) {
      user = await this.userService.create({
        id: `u-gh-${Date.now()}`,
        username: githubUser.username,
        email: githubUser.email,
        name: 'GitHub User',
        avatar: githubUser.avatar,
      });
    }

    const loginResponse = await this.signIn(user.username, '');

    // Redirect to Frontend with token
    // NOTE: Frontend should handle extracting token from URL
    res.redirect(
      `http://localhost:5173/login?token=${loginResponse.access_token}&userId=${loginResponse.user.id}`,
    );
  }
}
