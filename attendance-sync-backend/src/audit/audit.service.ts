import { Injectable, Logger } from '@nestjs/common';
import { AuditAction, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class AuditService {
  private readonly logger = new Logger(AuditService.name);

  constructor(private readonly prisma: PrismaService) {}

  async log(params: {
    action: AuditAction;
    entityType: string;
    entityId?: string;
    schoolId?: string;
    deviceId?: string;
    actorType?: string;
    actorId?: string;
    metadata?: Prisma.InputJsonValue;
  }): Promise<void> {
    try {
      await this.prisma.auditLog.create({
        data: {
          action: params.action,
          entityType: params.entityType,
          entityId: params.entityId,
          schoolId: params.schoolId,
          deviceId: params.deviceId,
          actorType: params.actorType ?? 'DEVICE',
          actorId: params.actorId ?? params.deviceId,
          metadata: params.metadata ?? Prisma.JsonNull,
        },
      });
    } catch (error) {
      this.logger.warn(`Audit log write failed: ${(error as Error).message}`);
    }
  }
}
