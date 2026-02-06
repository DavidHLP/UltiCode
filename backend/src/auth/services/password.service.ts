import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { PrismaService } from '../../prisma.service';
import { UserService } from '../../user/user.service';
import { BusinessException } from '../../common/exceptions/business.exception';
import { ErrorCode } from '../../common/error-codes';
import { randomBytes } from 'crypto';

@Injectable()
export class PasswordService {
  private readonly PASSWORD_RESET_EXPIRY = 60 * 60 * 1000;

  constructor(
    private readonly userService: UserService,
    private readonly jwtService: JwtService,
    private readonly prisma: PrismaService,
  ) {}

  async hashPassword(password: string): Promise<string> {
    const bcrypt = await import('bcrypt');
    const saltRounds = 10;
    return bcrypt.hash(password, saltRounds);
  }

  async verifyPassword(
    password: string,
    hashedPassword: string,
  ): Promise<boolean> {
    const bcrypt = await import('bcrypt');
    return bcrypt.compare(password, hashedPassword);
  }

  async verifyCredentials(username: string, password: string) {
    const user = await this.userService.findByUsername(username);

    if (!user) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    if (!user.password) {
      throw new BusinessException(ErrorCode.AUTH_NO_PASSWORD);
    }

    const isPasswordValid = await this.verifyPassword(password, user.password);
    if (!isPasswordValid) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    return user;
  }

  async forgotPassword(email: string) {
    const user = await this.userService.findByEmail(email);
    if (!user) {
      return {
        message:
          'If an account exists with this email, a password reset link will be sent',
      };
    }

    await this.prisma.passwordReset.updateMany({
      where: { user_id: user.id },
      data: { used_at: new Date() },
    });

    const token = randomBytes(32).toString('hex');
    const expiresAt = new Date(Date.now() + this.PASSWORD_RESET_EXPIRY);

    await this.prisma.passwordReset.create({
      data: {
        user_id: user.id,
        token,
        expires_at: expiresAt,
      },
    });

    return {
      message:
        'If an account exists with this email, a password reset link will be sent',
    };
  }

  async resetPassword(token: string, newPassword: string) {
    const resetRecord = await this.prisma.passwordReset.findUnique({
      where: { token },
    });

    if (!resetRecord) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_RESET_TOKEN);
    }

    if (resetRecord.used_at) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_ALREADY_USED);
    }

    if (new Date() > resetRecord.expires_at) {
      throw new BusinessException(ErrorCode.AUTH_RESET_TOKEN_EXPIRED);
    }

    const hashedPassword = await this.hashPassword(newPassword);

    await this.userService.update(resetRecord.user_id, {
      password: hashedPassword,
    });

    await this.prisma.passwordReset.update({
      where: { id: resetRecord.id },
      data: { used_at: new Date() },
    });

    await this.prisma.passwordReset.updateMany({
      where: {
        user_id: resetRecord.user_id,
        id: { not: resetRecord.id },
      },
      data: { used_at: new Date() },
    });

    return { message: 'Password has been reset successfully' };
  }
}
