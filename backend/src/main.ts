import { NestFactory } from '@nestjs/core';
import { ValidationPipe } from '@nestjs/common';
import { AppModule } from './app.module';
import cookieParser from 'cookie-parser';

// Fix BigInt serialization
declare global {
  interface BigInt {
    toJSON(): number;
  }
}
BigInt.prototype.toJSON = function (this: bigint) {
  return Number(this);
};

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Register cookie-parser middleware before CORS
  app.use(cookieParser());

  app.useGlobalPipes(
    new ValidationPipe({
      transform: true,
      whitelist: true,
      forbidNonWhitelisted: true,
    }),
  );

  app.enableCors({
    origin: [
      'http://127.0.0.1:5173',
      'http://127.0.0.1:6002',
      'http://127.0.0.1:6003',
      'http://localhost:5173',
      'http://localhost:6002',
      'http://localhost:6003',
    ],
    credentials: true,
  });
  await app.listen(process.env.PORT ?? 3000);
}
void bootstrap();
