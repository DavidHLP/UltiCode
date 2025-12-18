import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import { Response } from 'express';

@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>(); // Express Response
    const status =
      exception instanceof HttpException
        ? exception.getStatus()
        : HttpStatus.INTERNAL_SERVER_ERROR;

    const code =
      status === 400
        ? 400000
        : status === 401
          ? 401000
          : status === 403
            ? 403000
            : status === 404
              ? 404000
              : status === 409
                ? 409000
                : 500000;

    const message =
      exception instanceof HttpException
        ? exception.message
        : 'Internal Server Error';

    response.status(status).json({
      code,
      message,
      data: null,
      traceId: `t-${Date.now()}`,
    });
  }
}
