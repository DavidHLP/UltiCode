import {
  Controller,
  Get,
  Patch,
  Post,
  Body,
  UseGuards,
} from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AccountService } from '../services/account.service';
import { UpdateProfileDto, ChangePasswordDto } from '../dto/account.dto';
import { User } from '../../user/user.entity';

@Controller('admin/account')
@UseGuards(AuthGuard, CsrfGuard)
export class AdminAccountController {
  constructor(private readonly accountService: AccountService) {}

  @Get('profile')
  async getProfile(@CurrentAdmin() user: User) {
    return this.accountService.getProfile(user.id);
  }

  @Patch('profile')
  async updateProfile(
    @CurrentAdmin() user: User,
    @Body() updateProfileDto: UpdateProfileDto,
  ) {
    return this.accountService.updateProfile(user.id, updateProfileDto);
  }

  @Post('change-password')
  async changePassword(
    @CurrentAdmin() user: User,
    @Body() changePasswordDto: ChangePasswordDto,
  ) {
    return this.accountService.changePassword(user.id, changePasswordDto);
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
