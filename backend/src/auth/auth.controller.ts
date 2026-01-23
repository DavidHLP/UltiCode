import type { Request } from 'express';
import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Post,
  Query,
  Req,
  Res,
  UseGuards,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import type { Response } from 'express';
import { AuthService } from './auth.service';
import { SignInDto } from './dto/sign-in.dto';
import { RegisterDto } from './dto/register.dto';
import { ForgotPasswordDto, ResetPasswordDto } from './dto/reset-password.dto';
import { AuthGuard } from './auth.guard';

@Controller('auth')
export class AuthController {
  constructor(private authService: AuthService) {}

  @HttpCode(HttpStatus.OK)
  @Post('login')
  @Throttle({ default: { limit: 5, ttl: 60000 } }) // 5 login attempts per minute
  async signIn(@Body() signInDto: SignInDto, @Res() res: Response) {
    const result = await this.authService.signIn(
      signInDto.username,
      signInDto.password,
      res,
    );
    return res.json(result);
  }

  @HttpCode(HttpStatus.CREATED)
  @Post('register')
  @Throttle({ default: { limit: 3, ttl: 300000 } }) // 3 registrations per 5 minutes
  async register(@Body() registerDto: RegisterDto, @Res() res: Response) {
    const result = await this.authService.register(registerDto, res);
    return res.json(result);
  }

  @HttpCode(HttpStatus.OK)
  @Post('forgot-password')
  @Throttle({ default: { limit: 3, ttl: 300000 } }) // 3 password reset requests per 5 minutes
  async forgotPassword(@Body() forgotPasswordDto: ForgotPasswordDto) {
    return this.authService.forgotPassword(forgotPasswordDto.email);
  }

  @HttpCode(HttpStatus.OK)
  @Post('reset-password')
  @Throttle({ default: { limit: 10, ttl: 300000 } }) // 10 resets per 5 minutes
  async resetPassword(@Body() resetPasswordDto: ResetPasswordDto) {
    return this.authService.resetPassword(resetPasswordDto);
  }

  @HttpCode(HttpStatus.OK)
  @Get('github')
  githubLogin(@Res() res: Response) {
    this.authService.githubLogin(res);
  }

  @HttpCode(HttpStatus.OK)
  @Get('github/callback')
  async githubCallback(@Query('code') code: string, @Res() res: Response) {
    await this.authService.githubCallback(code, res);
  }

  @HttpCode(HttpStatus.OK)
  @Post('logout')
  @UseGuards(AuthGuard)
  async logout(@Req() req: Request, @Res() res: Response) {
    const token = this.extractTokenFromHeader(req);
    const result = await this.authService.logout({ token: token || '' }, res);
    return res.json(result);
  }

  @HttpCode(HttpStatus.OK)
  @Post('refresh')
  async refresh(@Req() req: Request, @Res() res: Response) {
    const cookies = req.cookies as { refresh_token?: string } | undefined;
    const refreshToken = cookies?.refresh_token;
    if (!refreshToken) {
      return res
        .status(HttpStatus.UNAUTHORIZED)
        .json({ message: 'No refresh token provided' });
    }

    const result = await this.authService.refreshTokens(refreshToken, res);
    return res.json(result);
  }

  private extractTokenFromHeader(request: Request): string | undefined {
    const [type, token] = (request.headers.authorization?.split(' ') ?? []) as [
      string | undefined,
      string | undefined,
    ];
    return type === 'Bearer' ? token : undefined;
  }
}
