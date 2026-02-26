import {
  Controller,
  Get,
  Param,
  UseGuards,
  Patch,
  Body,
  Req,
  BadRequestException,
} from '@nestjs/common';
import { Request } from 'express';
import { UserService, User } from './user.service';
import { AuthGuard } from '../auth/auth.guard';

interface AuthenticatedRequest extends Request {
  user: {
    id: string;
  };
}

@Controller('users')
export class UserController {
  constructor(private readonly userService: UserService) {}

  @Get()
  findAll(): Promise<User[]> {
    return this.userService.findAll();
  }

  @Get(':id')
  findOne(
    @Param('id') id: string,
  ): Promise<(User & { rank: number | null }) | null> {
    return this.userService.getProfileWithRank(id);
  }

  @UseGuards(AuthGuard)
  @Patch(':id')
  async updateProfile(
    @Param('id') id: string,
    @Body() userData: Partial<User>,
    @Req() req: AuthenticatedRequest,
  ) {
    // Ensure the user is updating their own profile
    if (req.user.id !== id) {
      throw new BadRequestException('You can only update your own profile');
    }
    return this.userService.update(id, userData);
  }

  @Get(':id/stats')
  getUserStats(@Param('id') id: string) {
    return this.userService.getUserStats(id);
  }

  @Get(':id/skills')
  getUserSkills(@Param('id') id: string) {
    return this.userService.getUserSkills(id);
  }
}
