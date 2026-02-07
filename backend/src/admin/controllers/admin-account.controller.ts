import { Controller, Get, Patch, Post, Body, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AccountService } from '../services/account.service';
import { UserService } from '../../user/user.service';
import { AuditService } from '../services/audit.service';
import { UpdateProfileDto } from '../dto/account.dto';
import { ChangePasswordDto } from '../../user/dto/change-password.dto';
import type { User } from '../../user/user.service';

@Controller('admin/account')
@UseGuards(AuthGuard, CsrfGuard)
export class AdminAccountController {
  constructor(
    private readonly accountService: AccountService,
    private readonly userService: UserService,
    private readonly auditService: AuditService,
  ) {}

  @Get('profile')
  async getProfile(@CurrentAdmin() user: User) {
    const profile = await this.userService.findOne(user.id);
    if (!profile) {
      return null;
    }
    return profile;
  }

  @Patch('profile')
  async updateProfile(
    @CurrentAdmin() user: User,
    @Body() updateProfileDto: UpdateProfileDto,
  ) {
    const oldUser = await this.userService.findOne(user.id);
    if (!oldUser) {
      return null;
    }

    // Update the user profile
    const updatedUser = await this.userService.update(
      user.id,
      updateProfileDto,
    );

    // Log the change
    await this.auditService.log({
      performerId: user.id,
      action: 'UPDATE_PROFILE',
      entityType: 'USER',
      entityId: user.id,
      userId: user.id,
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

    return updatedUser;
  }

  @Post('change-password')
  async changePassword(
    @CurrentAdmin() user: User,
    @Body() changePasswordDto: ChangePasswordDto,
  ) {
    return this.userService.changePassword(user.id, changePasswordDto, user.id);
  }

  @Get('subscription')
  async getSubscription(@CurrentAdmin() user: User) {
    const subscription = await this.accountService.getSubscription(user.id);

    // If no subscription, return a default free plan response
    if (!subscription) {
      return {
        id: 'free',
        plan: 'FREE',
        status: 'ACTIVE',
        started_at: user.joined_at,
        expires_at: null,
        cancelled_at: null,
      };
    }

    return subscription;
  }
}
