import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Response } from 'express';

@Injectable()
export class CookieService {
  private readonly ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000;
  private readonly REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000;

  constructor(private configService: ConfigService) {}

  setAuthCookies(
    res: Response,
    accessToken: string,
    refreshToken: string,
  ): void {
    const isSecure = this.configService.get<boolean>('COOKIE_SECURE', false);
    const sameSite = this.configService.get<'strict' | 'lax' | 'none'>(
      'COOKIE_SAME_SITE',
      'lax',
    );
    const domain = this.configService.get<string>('COOKIE_DOMAIN');

    res.cookie('access_token', accessToken, {
      httpOnly: true,
      secure: isSecure,
      sameSite,
      domain,
      maxAge: this.ACCESS_TOKEN_EXPIRY,
    });

    res.cookie('refresh_token', refreshToken, {
      httpOnly: true,
      secure: isSecure,
      sameSite,
      domain,
      maxAge: this.REFRESH_TOKEN_EXPIRY,
    });
  }

  clearAuthCookies(res: Response): void {
    const domain = this.configService.get<string>('COOKIE_DOMAIN');

    res.clearCookie('access_token', { domain });
    res.clearCookie('refresh_token', { domain });
  }

  getAccessTokenExpiry(): number {
    return this.ACCESS_TOKEN_EXPIRY;
  }

  getRefreshTokenExpiry(): number {
    return this.REFRESH_TOKEN_EXPIRY;
  }
}
