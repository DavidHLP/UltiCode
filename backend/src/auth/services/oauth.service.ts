import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Response } from 'express';
import { PrismaService } from '../../prisma.service';
import { UserService } from '../../user/user.service';
import { PasswordService } from './password.service';
import { TokenService } from './token.service';
import { CookieService } from './cookie.service';
import { CsrfService } from '../csrf.service';
import { RefreshTokenService } from '../refresh-token.service';
import { randomUUID } from 'crypto';

interface GitHubUser {
  id: number;
  login: string;
  email: string | null;
  name: string | null;
  avatar_url: string;
}

interface GoogleUser {
  id: string;
  email: string;
  name: string;
  picture: string;
}

@Injectable()
export class OAuthService {
  private readonly logger = new Logger(OAuthService.name);

  constructor(
    private readonly configService: ConfigService,
    private readonly prisma: PrismaService,
    private readonly userService: UserService,
    private readonly passwordService: PasswordService,
    private readonly tokenService: TokenService,
    private readonly cookieService: CookieService,
    private readonly csrfService: CsrfService,
    private readonly refreshTokenService: RefreshTokenService,
  ) {}

  githubLogin(res: Response): void {
    const clientId = this.configService.get<string>(
      'GITHUB_CLIENT_ID',
      'mock_client_id',
    );
    const redirectUri = this.configService.get<string>(
      'GITHUB_REDIRECT_URI',
      'http://localhost:4001/auth/github/callback',
    );
    const target = `https://github.com/login/oauth/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&scope=user:email`;
    res.redirect(target);
  }

  async githubCallback(code: string, res: Response): Promise<void> {
    const clientId = this.configService.get<string>('GITHUB_CLIENT_ID');
    const clientSecret = this.configService.get<string>('GITHUB_CLIENT_SECRET');
    const redirectUri = this.configService.get<string>(
      'GITHUB_REDIRECT_URI',
      'http://localhost:4001/auth/github/callback',
    );

    let githubUser: GitHubUser;

    // If credentials are not configured, use mock data for development
    if (!clientId || !clientSecret) {
      this.logger.warn(
        'GitHub OAuth credentials not configured, using mock data',
      );
      githubUser = {
        id: 12345,
        login: 'github_dev_user',
        email: 'github_dev@example.com',
        name: 'GitHub Dev User',
        avatar_url: 'https://avatars.githubusercontent.com/u/12345?v=4',
      };
    } else {
      // Exchange code for access token
      const tokenResponse = await fetch(
        'https://github.com/login/oauth/access_token',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
          },
          body: JSON.stringify({
            client_id: clientId,
            client_secret: clientSecret,
            code,
            redirect_uri: redirectUri,
          }),
        },
      );

      const tokenData = (await tokenResponse.json()) as {
        access_token?: string;
        error?: string;
      };

      if (!tokenData.access_token) {
        this.logger.error(`GitHub token exchange failed: ${tokenData.error}`);
        const frontendUrl = this.configService.get<string>(
          'FRONTEND_URL',
          'http://localhost:5173',
        );
        return res.redirect(
          `${frontendUrl}/login?error=oauth_failed`,
        );
      }

      // Fetch user info
      const userResponse = await fetch('https://api.github.com/user', {
        headers: {
          Authorization: `Bearer ${tokenData.access_token}`,
          Accept: 'application/vnd.github.v3+json',
        },
      });

      githubUser = (await userResponse.json()) as GitHubUser;

      // If user has no public email, fetch primary email
      if (!githubUser.email) {
        const emailResponse = await fetch(
          'https://api.github.com/user/emails',
          {
            headers: {
              Authorization: `Bearer ${tokenData.access_token}`,
              Accept: 'application/vnd.github.v3+json',
            },
          },
        );
        const emails = (await emailResponse.json()) as Array<{
          email: string;
          primary: boolean;
          verified: boolean;
        }>;
        const primaryEmail = emails.find((e) => e.primary && e.verified);
        githubUser.email = primaryEmail?.email || null;
      }
    }

    if (!githubUser.email) {
      const frontendUrl = this.configService.get<string>(
        'FRONTEND_URL',
        'http://localhost:5173',
      );
      return res.redirect(
        `${frontendUrl}/login?error=no_email`,
      );
    }

    let user = await this.userService.findByEmail(githubUser.email);
    if (!user) {
      user = await this.userService.create({
        id: randomUUID(),
        username: githubUser.login,
        email: githubUser.email,
        name: githubUser.name || githubUser.login,
        avatar: githubUser.avatar_url,
      });
    } else if (!user.avatar && githubUser.avatar_url) {
      // Update avatar if user didn't have one
      await this.userService.update(user.id, { avatar: githubUser.avatar_url });
    }

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

    const frontendUrl = this.configService.get<string>(
      'FRONTEND_URL',
      'http://localhost:5173',
    );
    res.redirect(`${frontendUrl}/?csrf=${csrfToken}`);
  }

  googleLogin(res: Response): void {
    const clientId = this.configService.get<string>(
      'GOOGLE_CLIENT_ID',
      'mock_client_id',
    );
    const redirectUri = this.configService.get<string>(
      'GOOGLE_REDIRECT_URI',
      'http://localhost:4001/auth/google/callback',
    );
    const scope = encodeURIComponent('openid email profile');
    const target = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code&scope=${scope}`;
    res.redirect(target);
  }

  async googleCallback(code: string, res: Response): Promise<void> {
    const clientId = this.configService.get<string>('GOOGLE_CLIENT_ID');
    const clientSecret = this.configService.get<string>('GOOGLE_CLIENT_SECRET');
    const redirectUri = this.configService.get<string>(
      'GOOGLE_REDIRECT_URI',
      'http://localhost:4001/auth/google/callback',
    );

    let googleUser: GoogleUser;

    // If credentials are not configured, use mock data for development
    if (!clientId || !clientSecret) {
      this.logger.warn(
        'Google OAuth credentials not configured, using mock data',
      );
      googleUser = {
        id: 'google_dev_123',
        email: 'google_dev@example.com',
        name: 'Google Dev User',
        picture: 'https://lh3.googleusercontent.com/default_photo',
      };
    } else {
      // Exchange code for access token
      const tokenResponse = await fetch(
        'https://oauth2.googleapis.com/token',
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: new URLSearchParams({
            client_id: clientId,
            client_secret: clientSecret,
            code,
            redirect_uri: redirectUri,
            grant_type: 'authorization_code',
          }).toString(),
        },
      );

      const tokenData = (await tokenResponse.json()) as {
        access_token?: string;
        error?: string;
      };

      if (!tokenData.access_token) {
        this.logger.error(`Google token exchange failed: ${tokenData.error}`);
        const frontendUrl = this.configService.get<string>(
          'FRONTEND_URL',
          'http://localhost:5173',
        );
        return res.redirect(
          `${frontendUrl}/login?error=oauth_failed`,
        );
      }

      // Fetch user info
      const userResponse = await fetch(
        'https://www.googleapis.com/oauth2/v2/userinfo',
        {
          headers: {
            Authorization: `Bearer ${tokenData.access_token}`,
          },
        },
      );

      googleUser = (await userResponse.json()) as GoogleUser;
    }

    if (!googleUser.email) {
      const frontendUrl = this.configService.get<string>(
        'FRONTEND_URL',
        'http://localhost:5173',
      );
      return res.redirect(
        `${frontendUrl}/login?error=no_email`,
      );
    }

    let user = await this.userService.findByEmail(googleUser.email);
    if (!user) {
      // Generate a unique username from email
      const baseUsername = googleUser.email.split('@')[0];
      let username = baseUsername;
      let counter = 1;
      while (await this.userService.findByUsername(username)) {
        username = `${baseUsername}${counter}`;
        counter++;
      }

      user = await this.userService.create({
        id: randomUUID(),
        username,
        email: googleUser.email,
        name: googleUser.name || username,
        avatar: googleUser.picture,
      });
    } else if (!user.avatar && googleUser.picture) {
      await this.userService.update(user.id, { avatar: googleUser.picture });
    }

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

    const frontendUrl = this.configService.get<string>(
      'FRONTEND_URL',
      'http://localhost:5173',
    );
    res.redirect(`${frontendUrl}/?csrf=${csrfToken}`);
  }
}
