import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { Response } from 'express';
import { AuthService } from './auth.service';
import { UserService } from '../user/user.service';
import { PrismaService } from '../prisma.service';
import { TokenBlacklistService } from './token-blacklist.service';
import { CsrfService } from './csrf.service';
import { RefreshTokenService } from './refresh-token.service';
import { PasswordService } from './services/password.service';
import { TokenService } from './services/token.service';
import { CookieService } from './services/cookie.service';
import { OAuthService } from './services/oauth.service';
import { RegisterDto } from './dto/register.dto';
import { UserRole } from '../user/user.service';

describe('AuthService', () => {
  let service: AuthService;
  let userService: jest.Mocked<UserService>;
  let passwordService: jest.Mocked<PasswordService>;
  let tokenService: jest.Mocked<TokenService>;
  let cookieService: jest.Mocked<CookieService>;
  let oauthService: jest.Mocked<OAuthService>;
  let refreshTokenService: jest.Mocked<RefreshTokenService>;
  let tokenBlacklistService: jest.Mocked<TokenBlacklistService>;

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
            generateToken: jest.fn().mockReturnValue('refresh-token'),
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
            $transaction: jest.fn().mockImplementation((callback) => {
              // Create a mock transaction client that has the same methods as prisma
              const tx = {
                user: {
                  create: jest.fn().mockResolvedValue(mockUser),
                  update: jest.fn().mockResolvedValue(mockUser),
                  findUnique: jest.fn().mockResolvedValue(mockUser),
                },
                refreshToken: {
                  create: jest.fn().mockResolvedValue({
                    token: 'refresh-token',
                    user_id: 'user-123',
                  }),
                },
                auditLog: {
                  create: jest.fn().mockResolvedValue({}),
                },
                passwordReset: {
                  updateMany: jest.fn().mockResolvedValue({ count: 1 }),
                  create: jest.fn().mockResolvedValue({}),
                  findUnique: jest.fn().mockResolvedValue(null),
                  update: jest.fn().mockResolvedValue({}),
                },
              };
              return callback(tx);
            }),
            passwordReset: {
              updateMany: jest.fn().mockResolvedValue({ count: 1 }),
              create: jest.fn().mockResolvedValue({}),
              findUnique: jest.fn().mockResolvedValue(null),
              update: jest.fn().mockResolvedValue({}),
            },
          },
        },
        {
          provide: PasswordService,
          useValue: {
            hashPassword: jest.fn().mockResolvedValue('hashed'),
            verifyPassword: jest.fn().mockResolvedValue(true),
            verifyCredentials: jest.fn().mockResolvedValue(mockUser),
            forgotPassword: jest.fn().mockResolvedValue({
              message:
                'If an account exists with this email, a password reset link will be sent',
            }),
            resetPassword: jest.fn().mockResolvedValue({
              message: 'Password has been reset successfully',
            }),
          },
        },
        {
          provide: TokenService,
          useValue: {
            generateAccessToken: jest.fn().mockReturnValue('jwt-token'),
            decodeToken: jest.fn().mockReturnValue({
              exp: Math.floor(Date.now() / 1000) + 3600,
              sub: 'user-123',
            }),
            getUserIdFromToken: jest.fn().mockReturnValue('user-123'),
            getTokenExpiry: jest.fn().mockReturnValue(3600),
            verifyToken: jest.fn().mockReturnValue({
              exp: Math.floor(Date.now() / 1000) + 3600,
              sub: 'user-123',
            }),
          },
        },
        {
          provide: CookieService,
          useValue: {
            setAuthCookies: jest.fn(),
            clearAuthCookies: jest.fn(),
          },
        },
        {
          provide: OAuthService,
          useValue: {
            githubLogin: jest.fn(),
            githubCallback: jest.fn().mockResolvedValue(undefined),
          },
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
    userService = module.get(UserService);
    passwordService = module.get(PasswordService);
    tokenService = module.get(TokenService);
    cookieService = module.get(CookieService);
    oauthService = module.get(OAuthService);
    refreshTokenService = module.get(RefreshTokenService);
    tokenBlacklistService = module.get(TokenBlacklistService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('signIn', () => {
    it('should return login response with valid credentials', async () => {
      passwordService.verifyCredentials.mockResolvedValue(mockUser);
      tokenService.generateAccessToken.mockReturnValue('jwt-token');

      const res = mockResponse();
      const result = await service.signIn('testuser', 'password123', res);

      expect(result).toHaveProperty('user');
      expect(result.user).toEqual({
        id: mockUser.id,
        username: mockUser.username,
        name: mockUser.name,
        role: mockUser.role,
      });
      expect(cookieService.setAuthCookies).toHaveBeenCalled();
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
      userService.create.mockResolvedValue(mockUser);
      tokenService.generateAccessToken.mockReturnValue('jwt-token');
      passwordService.hashPassword.mockResolvedValue('hashed');

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
      tokenService.getTokenExpiry.mockReturnValue(3600);
      tokenService.getUserIdFromToken.mockReturnValue('user-123');

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
      userService.findByEmail.mockResolvedValue(mockUser);
      passwordService.forgotPassword.mockResolvedValue({
        message:
          'If an account exists with this email, a password reset link will be sent',
      });

      const result = await service.forgotPassword('test@example.com');

      expect(result).toHaveProperty('message');
      expect(result.message).toContain('password reset link will be sent');
    });

    it('should return same message when user does not exist', async () => {
      userService.findByEmail.mockResolvedValue(null);
      passwordService.forgotPassword.mockResolvedValue({
        message:
          'If an account exists with this email, a password reset link will be sent',
      });

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

      passwordService.resetPassword.mockResolvedValue({
        message: 'Password has been reset successfully',
      });

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

      expect(oauthService.githubLogin).toHaveBeenCalledWith(res);
    });
  });

  describe('githubCallback', () => {
    it('should call oauth service', async () => {
      const res = mockResponse();
      res.redirect = jest.fn().mockReturnThis();

      oauthService.githubCallback.mockResolvedValue(undefined);

      await service.githubCallback('github-code', res);

      expect(oauthService.githubCallback).toHaveBeenCalledWith(
        'github-code',
        res,
      );
    });
  });
});
