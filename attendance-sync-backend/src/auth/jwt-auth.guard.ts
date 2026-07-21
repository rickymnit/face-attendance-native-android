import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { DeviceStatus } from '@prisma/client';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly jwtService: JwtService,
    private readonly prisma: PrismaService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const token = this.extractBearerToken(request.headers.authorization);
    if (!token) throw new UnauthorizedException('Missing bearer token');

    const payload = await this.jwtService.verifyAsync<{ deviceId: string; schoolId: string }>(token).catch(() => null);
    if (!payload?.deviceId || !payload.schoolId) {
      throw new UnauthorizedException('Invalid bearer token');
    }

    const device = await this.prisma.device.findFirst({
      where: {
        id: payload.deviceId,
        schoolId: payload.schoolId,
        status: DeviceStatus.ACTIVE,
      },
      select: { id: true, schoolId: true },
    });
    if (!device) throw new UnauthorizedException('Device is not authorized');

    request.deviceAuth = { deviceId: device.id, schoolId: device.schoolId };
    return true;
  }

  private extractBearerToken(header: string | undefined): string | null {
    if (!header) return null;
    const [scheme, token] = header.split(' ');
    return scheme?.toLowerCase() === 'bearer' && token ? token : null;
  }
}
