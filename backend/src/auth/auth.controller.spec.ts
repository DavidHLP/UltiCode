import { Test, TestingModule } from '@nestjs/testing';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { SignInDto } from './dto/sign-in.dto';
import { RegisterDto } from './dto/register.dto';
import { ForgotPasswordDto, ResetPasswordDto } from './dto/reset-password.dto';
import { Response, Request } from 'express';
import { JwtService } from '@nestjs/jwt';
import { Reflector, ModuleRef } from '@nestjs/core';
import { AuthGuard } from './auth.guard';

describe('AuthController', () => {
  let controller: AuthController;
  let service: jest.Mocked<AuthService>;

  const mockLoginResponse = {
    access_token: 'jwt-token',
    csrf_token: 'mock-csrf-token',
    user: {
      id: 'user-123',
      username: 'testuser',
      name: 'Test User',
      role: 'USER',
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [AuthController],
      providers: [
        {
          provide: JwtService,
          useValue: {
            sign: jest.fn(),
            verify: jest.fn(),
          },
        },
        {
          provide: Reflector,
          useValue: {
            get: jest.fn(),
            getAll: jest.fn(),
          },
        },
        {
          provide: ModuleRef,
          useValue: {
            get: jest.fn(),
          },
        },
        {
          provide: AuthService,
          useValue: {
            signIn: jest.fn(),
            register: jest.fn(),
            forgotPassword: jest.fn(),
            resetPassword: jest.fn(),
            githubLogin: jest.fn(),
            githubCallback: jest.fn(),
            logout: jest.fn(),
          },
        },
      ],
    })
      .overrideGuard(AuthGuard)
      .useValue({ canActivate: jest.fn(() => true) })
      .compile();

    controller = module.get<AuthController>(AuthController);
    service = module.get(AuthService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('login', () => {
    it('should return login response', async () => {
      const signInDto: SignInDto = {
        username: 'testuser',
        password: 'password123',
      };

      service.signIn.mockResolvedValue(mockLoginResponse);

      const result = await controller.signIn(signInDto);

      expect(result).toEqual(mockLoginResponse);
      expect(service.signIn).toHaveBeenCalledWith('testuser', 'password123');
    });
  });

  describe('register', () => {
    it('should create user and return login response', async () => {
      const registerDto: RegisterDto = {
        username: 'newuser',
        email: 'new@example.com',
        password: 'password123',
      };

      service.register.mockResolvedValue(mockLoginResponse);

      const result = await controller.register(registerDto);

      expect(result).toEqual(mockLoginResponse);
      expect(service.register).toHaveBeenCalledWith(registerDto);
    });
  });

  describe('forgotPassword', () => {
    it('should return message for password reset request', async () => {
      const forgotPasswordDto: ForgotPasswordDto = {
        email: 'test@example.com',
      };

      const messageResponse = {
        message:
          'If an account exists with this email, a password reset link will be sent',
      };

      service.forgotPassword.mockResolvedValue(messageResponse);

      const result = await controller.forgotPassword(forgotPasswordDto);

      expect(result).toEqual(messageResponse);
      expect(service.forgotPassword).toHaveBeenCalledWith('test@example.com');
    });
  });

  describe('resetPassword', () => {
    it('should reset password and return success message', async () => {
      const resetPasswordDto: ResetPasswordDto = {
        token: 'reset-token',
        newPassword: 'newPassword123',
      };

      const messageResponse = {
        message: 'Password has been reset successfully',
      };

      service.resetPassword.mockResolvedValue(messageResponse);

      const result = await controller.resetPassword(resetPasswordDto);

      expect(result).toEqual(messageResponse);
      expect(service.resetPassword).toHaveBeenCalledWith(resetPasswordDto);
    });
  });

  describe('githubLogin', () => {
    it('should redirect to GitHub OAuth', () => {
      const res = {
        redirect: jest.fn(),
      } as unknown as Response;

      controller.githubLogin(res);

      expect(service.githubLogin).toHaveBeenCalledWith(res);
    });
  });

  describe('githubCallback', () => {
    it('should handle GitHub OAuth callback', async () => {
      const res = {
        redirect: jest.fn(),
      } as unknown as Response;

      service.githubCallback.mockResolvedValue(undefined);

      await controller.githubCallback('github-code', res);

      expect(service.githubCallback).toHaveBeenCalledWith('github-code', res);
    });
  });

  describe('logout', () => {
    it('should logout user and return success message', async () => {
      const mockRequest = {
        headers: {
          authorization: 'Bearer valid-jwt-token',
        },
      } as unknown as Request;

      const messageResponse = { message: 'Logged out successfully' };

      service.logout.mockResolvedValue(messageResponse);

      const result = await controller.logout(mockRequest);

      expect(result).toEqual(messageResponse);
      expect(service.logout).toHaveBeenCalledWith({ token: 'valid-jwt-token' });
    });

    it('should logout even without token', async () => {
      const mockRequest = {
        headers: {},
      } as unknown as Request;

      const messageResponse = { message: 'Logged out successfully' };

      service.logout.mockResolvedValue(messageResponse);

      const result = await controller.logout(mockRequest);

      expect(result).toEqual(messageResponse);
      expect(service.logout).toHaveBeenCalledWith({ token: '' });
    });

    it('should handle malformed authorization header', async () => {
      const mockRequest = {
        headers: {
          authorization: 'InvalidFormat token',
        },
      } as unknown as Request;

      const messageResponse = { message: 'Logged out successfully' };

      service.logout.mockResolvedValue(messageResponse);

      const result = await controller.logout(mockRequest);

      expect(result).toEqual(messageResponse);
      expect(service.logout).toHaveBeenCalledWith({ token: '' });
    });
  });
});
