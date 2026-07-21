import { HttpException, HttpStatus, Injectable, NestMiddleware } from '@nestjs/common';
import { NextFunction, Request, Response } from 'express';

interface Bucket {
  count: number;
  resetAt: number;
}

@Injectable()
export class DeviceRateLimitMiddleware implements NestMiddleware {
  private readonly buckets = new Map<string, Bucket>();

  use(request: Request, _response: Response, next: NextFunction): void {
    const windowMillis = Number.parseInt(process.env.RATE_LIMIT_WINDOW_MS ?? '60000', 10);
    const maxRequests = Number.parseInt(process.env.RATE_LIMIT_MAX_REQUESTS ?? '120', 10);
    const key = this.keyFor(request);
    const now = Date.now();
    const existing = this.buckets.get(key);
    const bucket = !existing || existing.resetAt <= now ? { count: 0, resetAt: now + windowMillis } : existing;
    bucket.count += 1;
    this.buckets.set(key, bucket);
    if (bucket.count > maxRequests) {
      throw new HttpException('Too many device API requests', HttpStatus.TOO_MANY_REQUESTS);
    }
    next();
  }

  private keyFor(request: Request): string {
    const deviceId = request.header('x-schoollog-device-id');
    const schoolId = request.header('x-schoollog-school-id');
    return `${schoolId ?? 'unknown-school'}:${deviceId ?? request.ip ?? 'unknown-ip'}:${request.path}`;
  }
}
