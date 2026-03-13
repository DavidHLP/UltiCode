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
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiCookieAuth,
} from '@nestjs/swagger';
import type { Response } from 'express';
import { AuthService } from './auth.service';
import { SignInDto } from './dto/sign-in.dto';
import { RegisterDto } from './dto/register.dto';
import { ForgotPasswordDto, ResetPasswordDto } from './dto/reset-password.dto';
import { AuthGuard } from './auth.guard';
import { extractTokenFromHeader } from './auth.utils';
import { CurrentUser } from './decorators/current-user.decorator';
import type { User } from '../user/user.service';
import { PermissionService } from '../admin/services/permission.service';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(
    private authService: AuthService,
    private permissionService: PermissionService,
  ) {}

  @HttpCode(HttpStatus.OK)
  @Post('login')
  @ApiOperation({
    summary: 'User login',
    description: 'Authenticate user and return tokens',
  })
  @ApiResponse({ status: 200, description: 'Login successful' })
  @ApiResponse({ status: 401, description: 'Invalid credentials' })
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
  @ApiOperation({
    summary: 'User registration',
    description: 'Create a new user account',
  })
  @ApiResponse({ status: 201, description: 'User registered successfully' })
  @ApiResponse({
    status: 400,
    description: 'Invalid input or user already exists',
  })
  async register(@Body() registerDto: RegisterDto, @Res() res: Response) {
    const result = await this.authService.register(registerDto, res);
    return res.json(result);
  }

  @HttpCode(HttpStatus.OK)
  @Post('forgot-password')
  @ApiOperation({
    summary: 'Request password reset',
    description: 'Send password reset email',
  })
  @ApiResponse({
    status: 200,
    description: 'Reset email sent if account exists',
  })
  async forgotPassword(@Body() forgotPasswordDto: ForgotPasswordDto) {
    return this.authService.forgotPassword(forgotPasswordDto.email);
  }

  @HttpCode(HttpStatus.OK)
  @Post('reset-password')
  @ApiOperation({
    summary: 'Reset password',
    description: 'Reset password using token from email',
  })
  @ApiResponse({ status: 200, description: 'Password reset successful' })
  @ApiResponse({ status: 400, description: 'Invalid or expired token' })
  async resetPassword(@Body() resetPasswordDto: ResetPasswordDto) {
    return this.authService.resetPassword(resetPasswordDto);
  }

  @HttpCode(HttpStatus.OK)
  @Get('github')
  @ApiOperation({
    summary: 'GitHub OAuth login',
    description: 'Redirect to GitHub OAuth',
  })
  @ApiResponse({ status: 200, description: 'Redirects to GitHub' })
  githubLogin(@Res() res: Response) {
    this.authService.githubLogin(res);
  }

  @HttpCode(HttpStatus.OK)
  @Get('github/callback')
  @ApiOperation({
    summary: 'GitHub OAuth callback',
    description: 'Handle GitHub OAuth callback',
  })
  @ApiResponse({
    status: 200,
    description: 'OAuth successful, redirects to app',
  })
  async githubCallback(@Query('code') code: string, @Res() res: Response) {
    await this.authService.githubCallback(code, res);
  }

  @HttpCode(HttpStatus.OK)
  @Get('google')
  @ApiOperation({
    summary: 'Google OAuth login',
    description: 'Redirect to Google OAuth',
  })
  @ApiResponse({ status: 200, description: 'Redirects to Google' })
  googleLogin(@Res() res: Response) {
    this.authService.googleLogin(res);
  }

  @HttpCode(HttpStatus.OK)
  @Get('google/callback')
  @ApiOperation({
    summary: 'Google OAuth callback',
    description: 'Handle Google OAuth callback',
  })
  @ApiResponse({
    status: 200,
    description: 'OAuth successful, redirects to app',
  })
  async googleCallback(@Query('code') code: string, @Res() res: Response) {
    await this.authService.googleCallback(code, res);
  }

  @HttpCode(HttpStatus.OK)
  @Post('logout')
  @UseGuards(AuthGuard)
  @ApiBearerAuth()
  @ApiCookieAuth()
  @ApiOperation({
    summary: 'User logout',
    description: 'Logout user and invalidate tokens',
  })
  @ApiResponse({ status: 200, description: 'Logout successful' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async logout(@Req() req: Request, @Res() res: Response) {
    const token = extractTokenFromHeader(req);
    const result = await this.authService.logout({ token: token || '' }, res);
    return res.json(result);
  }

  @HttpCode(HttpStatus.OK)
  @Post('refresh')
  @ApiCookieAuth()
  @ApiOperation({
    summary: 'Refresh tokens',
    description: 'Refresh access token using refresh token',
  })
  @ApiResponse({ status: 200, description: 'Tokens refreshed' })
  @ApiResponse({ status: 401, description: 'Invalid refresh token' })
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

  @Get('me')
  @UseGuards(AuthGuard)
  @ApiBearerAuth()
  @ApiOperation({
    summary: 'Get current user',
    description: 'Get the authenticated user profile',
  })
  @ApiResponse({ status: 200, description: 'Current user profile' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  getCurrentUser(@CurrentUser() user: User): User {
    return user;
  }

  @Get('permissions')
  @UseGuards(AuthGuard)
  @ApiBearerAuth()
  @ApiOperation({
    summary: 'Get user permissions',
    description: 'Get all permissions for the authenticated user',
  })
  @ApiResponse({ status: 200, description: 'List of user permissions' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async getPermissions(@CurrentUser() user: User) {
    const permissions = await this.permissionService.getUserPermissions(
      user.id,
    );
    return permissions.map((p) => `${p.action}:${p.resource}`);
  }
}
