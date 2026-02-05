import {
  Injectable,
  BadRequestException,
  NotFoundException,
} from '@nestjs/common';
import { UserService } from '../../user/user.service';
import { PrismaService } from '../../prisma.service';
import * as bcrypt from 'bcrypt';
import { UpdateProfileDto, ChangePasswordDto } from '../dto/account.dto';
import { AuditService } from './audit.service';

@Injectable()
export class AccountService {
  constructor(
    private userService: UserService,
    private auditService: AuditService,
    private prisma: PrismaService,
  ) {}

  async getProfile(userId: string) {
    const user = await this.userService.findOne(userId);
    if (!user) {
      throw new NotFoundException('User not found');
    }

    // Return the user profile data
    return {
      id: user.id,
      username: user.username,
      name: user.name,
      email: user.email,
      avatar: user.avatar,
      bio: user.bio,
      company: user.company,
      github: user.github,
      website: user.website,
      location: user.location,
      twitter: user.twitter,
      preferred_language: user.preferred_language,
      role: user.role,
      joined_at: user.joined_at,
      last_login_at: user.last_login_at,
    };
  }

  async updateProfile(userId: string, updateProfileDto: UpdateProfileDto) {
    const oldUser = await this.userService.findOne(userId);
    if (!oldUser) {
      throw new NotFoundException('User not found');
    }

    // Update the user profile
    const updatedUser = await this.userService.update(userId, updateProfileDto);

    // Log the change
    await this.auditService.log({
      performerId: userId,
      action: 'UPDATE_PROFILE',
      entityType: 'USER',
      entityId: userId,
      userId: userId,
      oldValues: {
        name: oldUser.name,
        email: oldUser.email,
        avatar: oldUser.avatar,
        bio: oldUser.bio,
        company: oldUser.company,
        github: oldUser.github,
        website: oldUser.website,
        location: oldUser.location,
        twitter: oldUser.twitter,
        preferred_language: oldUser.preferred_language,
      },
      newValues: updateProfileDto,
    });

    return {
      id: updatedUser.id,
      username: updatedUser.username,
      name: updatedUser.name,
      email: updatedUser.email,
      avatar: updatedUser.avatar,
      bio: updatedUser.bio,
      company: updatedUser.company,
      github: updatedUser.github,
      website: updatedUser.website,
      location: updatedUser.location,
      twitter: updatedUser.twitter,
      preferred_language: updatedUser.preferred_language,
      role: updatedUser.role,
      joined_at: updatedUser.joined_at,
      last_login_at: updatedUser.last_login_at,
    };
  }

  async changePassword(userId: string, changePasswordDto: ChangePasswordDto) {
    const user = await this.userService.findOne(userId);
    if (!user) {
      throw new NotFoundException('User not found');
    }

    if (!user.password) {
      throw new BadRequestException('User has no password set');
    }

    // Verify current password
    const isCurrentPasswordValid = await bcrypt.compare(
      changePasswordDto.currentPassword,
      user.password,
    );

    if (!isCurrentPasswordValid) {
      throw new BadRequestException('Current password is incorrect');
    }

    // Hash new password
    const hashedPassword = await bcrypt.hash(changePasswordDto.newPassword, 10);

    // Update password
    await this.userService.update(userId, {
      password: hashedPassword,
    });

    // Log the password change
    await this.auditService.log({
      performerId: userId,
      action: 'CHANGE_PASSWORD',
      entityType: 'USER',
      entityId: userId,
      userId: userId,
    });

    return { message: 'Password changed successfully' };
  }

  async getSubscription(userId: string) {
    const user = await this.userService.findOne(userId);
    if (!user) {
      throw new NotFoundException('User not found');
    }

    // Get subscription from Prisma
    const subscription = await this.prisma.subscription.findFirst({
      where: {
        user_id: userId,
      },
      orderBy: {
        started_at: 'desc',
      },
    });

    if (!subscription) {
      // Return null - no subscription means free plan
      return null;
    }

    return {
      id: subscription.id,
      plan: subscription.plan,
      status: subscription.status,
      started_at: subscription.started_at,
      expires_at: subscription.expires_at,
      cancelled_at: subscription.cancelled_at,
    };
  }
}
