import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { Response } from 'express';
import { AuthService } from './auth.service';
import { UserService } from '../user/user.service';
import { PrismaService } from '../prisma.service';
import { TokenBlacklistService } from './token-blacklist.service';
import { CsrfService } from './csrf.service';
import { RefreshTokenService } from './refresh-token.service';
import { RegisterDto } from './dto/register.dto';
import { UserRole } from '../user/user.service';

describe('AuthService', () => {
  let service: AuthService;
  let userService: jest.Mocked<UserService>;
  let jwtService: jest.Mocked<JwtService>;
  let tokenBlacklistService: jest.Mocked<TokenBlacklistService>;
  let refreshTokenService: jest.Mocked<RefreshTokenService>;
  let prisma: jest.Mocked<PrismaService>;

  const mockUser = {
    id: 'user-123',
    username: 'testuser',
    email: 'test@example.com',
    name: 'Test User',
    password: 'hashedpassword',
    role: UserRole.USER,
    avatar: null,
    joined_at: new Date(),
    is_active: true,
    is_banned: false,
    bio: null,
    website: null,
    github: null,
    twitter: null,
    location: null,
    company: null,
    preferred_language: null,
    banned_until: null,
    banned_reason: null,
    last_login_at: null,
    created_by: null,
    updated_by: null,
  };

  const mockResponse = () => {
    const res: Partial<Response> = {
      cookie: jest.fn().mockReturnThis(),
      clearCookie: jest.fn().mockReturnThis(),
      json: jest.fn().mockReturnThis(),
      status: jest.fn().mockReturnThis(),
    };
    return res as Response;
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: UserService,
          useValue: {
            findByUsername: jest.fn(),
            findByEmail: jest.fn(),
            create: jest.fn(),
            update: jest.fn(),
            findOne: jest.fn(),
          },
        },
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn().mockReturnValue('jwt-token'),
            decode: jest.fn(),
          },
        },
        {
          provide: TokenBlacklistService,
          useValue: {
            addToBlacklist: jest.fn(),
          },
        },
        {
          provide: CsrfService,
          useValue: {
            generateCsrfToken: jest.fn().mockResolvedValue('csrf-token'),
            revokeCsrfToken: jest.fn().mockResolvedValue(undefined),
            validateCsrfToken: jest.fn().mockResolvedValue(true),
          },
        },
        {
          provide: RefreshTokenService,
          useValue: {
            createRefreshToken: jest.fn().mockResolvedValue({
              token: 'refresh-token',
              user_id: 'user-123',
            }),
            rotateRefreshToken: jest.fn().mockResolvedValue({
              token: 'new-refresh-token',
              user_id: 'user-123',
            }),
            revokeRefreshToken: jest.fn(),
            revokeAllUserTokens: jest.fn(),
          },
        },
        {
          provide: PrismaService,
          useValue: {
            passwordReset: {
              updateMany: jest.fn().mockResolvedValue({ count: 1 }) as any,
              create: jest.fn().mockResolvedValue({}) as any,
              findUnique: jest.fn().mockResolvedValue(null) as any,
              update: jest.fn().mockResolvedValue({}) as any,
            },
          },
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
    userService = module.get(UserService);
    jwtService = module.get(JwtService);
    tokenBlacklistService = module.get(TokenBlacklistService);
    refreshTokenService = module.get(RefreshTokenService);
    prisma = module.get(PrismaService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('signIn', () => {
    it('should return login response with valid credentials', async () => {
      userService.findByUsername.mockResolvedValue(mockUser as any);
      (service as any).verifyPassword = jest.fn().mockResolvedValue(true);
      jwtService.sign.mockReturnValue('jwt-token');

      const res = mockResponse();
      const result = await service.signIn('testuser', 'password123', res);

      expect(result).toHaveProperty('user');
      expect(result.user).toEqual({
        id: mockUser.id,
        username: mockUser.username,
        name: mockUser.name,
        role: mockUser.role,
      });
      expect(res.cookie).toHaveBeenCalledWith(
        'access_token',
        'jwt-token',
        expect.any(Object),
      );
    });
  });

  describe('register', () => {
    it('should create new user and return login response', async () => {
      const registerDto: RegisterDto = {
        username: 'newuser',
        email: 'new@example.com',
        password: 'password123',
      };

      userService.findByUsername.mockResolvedValue(null);
      userService.findByEmail.mockResolvedValue(null);
      userService.create.mockResolvedValue(mockUser as any);
      jwtService.sign.mockReturnValue('jwt-token');
      (service as any).hashPassword = jest.fn().mockResolvedValue('hashed');

      const res = mockResponse();
      const result = await service.register(registerDto, res);

      expect(result).toHaveProperty('user');
      expect(result.user).toEqual({
        id: mockUser.id,
        username: mockUser.username,
        name: mockUser.name,
        role: mockUser.role,
      });
    });
  });

  describe('logout', () => {
    it('should add token to blacklist and return success message', async () => {
      const logoutDto = { token: 'valid-jwt-token' };
      jwtService.decode.mockReturnValue({
        exp: Math.floor(Date.now() / 1000) + 3600,
        sub: 'user-123',
      });

      const res = mockResponse();
      const result = await service.logout(logoutDto, res);

      expect(result).toEqual({ message: 'Logged out successfully' });
      expect(tokenBlacklistService.addToBlacklist).toHaveBeenCalled();
      expect(refreshTokenService.revokeAllUserTokens).toHaveBeenCalledWith(
        'user-123',
      );
    });

    it('should return success message even without token', async () => {
      const logoutDto = { token: '' };
      const res = mockResponse();

      const result = await service.logout(logoutDto, res);

      expect(result).toEqual({ message: 'Logged out successfully' });
    });
  });

  describe('forgotPassword', () => {
    it('should return message when user exists', async () => {
      userService.findByEmail.mockResolvedValue(mockUser as any);
      (prisma.passwordReset.updateMany as jest.Mock).mockResolvedValue({
        count: 1,
      });
      (prisma.passwordReset.create as jest.Mock).mockResolvedValue({} as never);

      const result = await service.forgotPassword('test@example.com');

      expect(result).toHaveProperty('message');
      expect(result.message).toContain('password reset link will be sent');
    });

    it('should return same message when user does not exist', async () => {
      userService.findByEmail.mockResolvedValue(null);

      const result = await service.forgotPassword('nonexistent@example.com');

      expect(result).toHaveProperty('message');
      expect(result.message).toContain('password reset link will be sent');
    });
  });

  describe('resetPassword', () => {
    it('should reset password with valid token', async () => {
      const resetPasswordDto = {
        token: 'valid-reset-token',
        newPassword: 'newPassword123',
      };

      (prisma.passwordReset.findUnique as jest.Mock).mockResolvedValue({
        id: 'reset-123',
        user_id: 'user-123',
        token: 'valid-reset-token',
        expires_at: new Date(Date.now() + 3600000),
        used_at: null,
      });

      userService.update.mockResolvedValue(mockUser as any);
      (prisma.passwordReset.update as jest.Mock).mockResolvedValue({} as never);
      (service as any).hashPassword = jest.fn().mockResolvedValue('new-hashed');

      const result = await service.resetPassword(resetPasswordDto);

      expect(result).toEqual({
        message: 'Password has been reset successfully',
      });
    });
  });

  describe('githubLogin', () => {
    it('should redirect to GitHub OAuth', () => {
      const res = {
        redirect: jest.fn(),
      } as unknown as Response;

      service.githubLogin(res);

      expect(res.redirect).toHaveBeenCalled();
      const redirectUrl = (res.redirect as jest.Mock).mock.calls[0][0];
      expect(redirectUrl).toContain('github.com/login/oauth/authorize');
    });
  });

  describe('githubCallback', () => {
    it('should create new user and redirect on first GitHub login', async () => {
      const res = mockResponse();
      res.redirect = jest.fn().mockReturnThis();

      userService.findByEmail.mockResolvedValue(null);
      userService.create.mockResolvedValue(mockUser as any);
      jwtService.sign.mockReturnValue('github-jwt-token');

      await service.githubCallback('github-code', res);

      expect(res.redirect).toHaveBeenCalled();
    });

    it('should use existing user and redirect on subsequent GitHub login', async () => {
      const res = mockResponse();
      res.redirect = jest.fn().mockReturnThis();

      userService.findByEmail.mockResolvedValue(mockUser as any);
      jwtService.sign.mockReturnValue('github-jwt-token');

      await service.githubCallback('github-code', res);

      expect(res.redirect).toHaveBeenCalled();
    });
  });
});
