import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
  Logger,
} from '@nestjs/common';
import { Response } from 'express';
import { BusinessException } from '../exceptions/business.exception';
import { ErrorCode } from '../error-codes';

interface RequestWithUser extends Request {
  user?: { id: string };
}

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(GlobalExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<RequestWithUser>();

    const method = request.method;
    const path = request.url;
    const traceId = `t-${Date.now()}`;
    const userId = request.user?.id || 'anonymous';

    let status: HttpStatus;
    let code: number;
    let message: string;
    let errorKey: string | undefined;

    if (exception instanceof BusinessException) {
      status = exception.getStatus();
      code = exception.errorCode;
      message = exception.message;
      errorKey = exception.errorKey;
    } else if (exception instanceof HttpException) {
      status = exception.getStatus();
      code = this.mapHttpStatusToErrorCode(status);
      message = exception.message;
    } else {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      code = ErrorCode.UNKNOWN_ERROR;
      message = 'Internal Server Error';
    }

    // Log errors with appropriate level based on status code
    const statusCode =
      typeof status === 'number'
        ? status
        : Number(HttpStatus.INTERNAL_SERVER_ERROR);
    if (statusCode >= 500) {
      this.logger.error(
        `HTTP ${statusCode} | ${method} ${path} | traceId: ${traceId} | userId: ${userId} | ${message}`,
        exception instanceof Error ? exception.stack : undefined,
      );
    } else if (statusCode >= 400) {
      this.logger.warn(
        `HTTP ${statusCode} | ${method} ${path} | traceId: ${traceId} | userId: ${userId} | ${message}`,
      );
    }

    response.status(status).json({
      code,
      message,
      errorKey,
      data: null,
      traceId,
    });
  }

  private mapHttpStatusToErrorCode(status: HttpStatus): number {
    switch (status) {
      case HttpStatus.BAD_REQUEST:
        return ErrorCode.BAD_REQUEST;
      case HttpStatus.UNAUTHORIZED:
        return ErrorCode.UNAUTHORIZED;
      case HttpStatus.FORBIDDEN:
        return ErrorCode.FORBIDDEN;
      case HttpStatus.NOT_FOUND:
        return ErrorCode.NOT_FOUND;
      case HttpStatus.CONFLICT:
        return ErrorCode.CONFLICT;
      default:
        return ErrorCode.UNKNOWN_ERROR;
    }
  }
}
