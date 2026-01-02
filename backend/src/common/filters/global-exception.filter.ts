import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { Response } from 'express';
import { BusinessException } from '../exceptions/business.exception';
import { ErrorCode } from '../error-codes';

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();

    let status: HttpStatus;
    let code: number;
    let message: string;
    let errorKey: string | undefined;

    if (exception instanceof BusinessException) {
      // Handle BusinessException with error codes
      status = exception.getStatus();
      code = exception.errorCode;
      message = exception.message;
      errorKey = exception.errorKey;
    } else if (exception instanceof HttpException) {
      // Handle standard HttpException
      status = exception.getStatus();
      code = this.mapHttpStatusToErrorCode(status);
      message = exception.message;
    } else {
      // Handle unknown exceptions
      status = HttpStatus.INTERNAL_SERVER_ERROR;
      code = ErrorCode.UNKNOWN_ERROR;
      message = 'Internal Server Error';
    }

    response.status(status).json({
      code,
      message,
      errorKey,
      data: null,
      traceId: `t-${Date.now()}`,
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
