import { Injectable } from '@nestjs/common';
import { Response } from 'express';

@Injectable()
export class CookieService {
  private readonly ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000;
  private readonly REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000;

  setAuthCookies(
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
    const domain = process.env.COOKIE_DOMAIN;

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
