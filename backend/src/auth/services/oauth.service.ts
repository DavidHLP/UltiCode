import { Injectable } from '@nestjs/common';
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

@Injectable()
export class OAuthService {
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
      });
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
