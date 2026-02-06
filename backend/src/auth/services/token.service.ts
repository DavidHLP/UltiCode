import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { PrismaService } from '../../prisma.service';
import { UserService } from '../../user/user.service';

export interface TokenPayload {
  sub: string;
  username: string;
  role: string;
  exp?: number;
}

@Injectable()
export class TokenService {
  constructor(
    private readonly jwtService: JwtService,
    private readonly userService: UserService,
    private readonly prisma: PrismaService,
  ) {}

  generateAccessToken(userId: string, username: string, role: string): string {
    const payload = { sub: userId, username, role };
    return this.jwtService.sign(payload);
  }

  decodeToken(token: string): TokenPayload | null | string {
    return this.jwtService.decode(token);
  }

  getUserIdFromToken(token: string): string | null {
    try {
      const decoded = this.decodeToken(token);
      if (decoded && typeof decoded === 'object' && 'sub' in decoded) {
        return decoded.sub;
      }
      return null;
    } catch {
      return null;
    }
  }

  verifyToken(token: string): TokenPayload | null {
    try {
      const decoded = this.jwtService.verify(token);
      if (decoded && typeof decoded === 'object') {
        return decoded as TokenPayload;
      }
      return null;
    } catch {
      return null;
    }
  }

  getTokenExpiry(token: string): number | null {
    try {
      const decoded = this.decodeToken(token);
      if (decoded && typeof decoded === 'object' && 'exp' in decoded) {
        const now = Math.floor(Date.now() / 1000);
        const ttl = decoded.exp! - now;
        return ttl > 0 ? ttl : 0;
      }
      return null;
    } catch {
      return null;
    }
  }
}
