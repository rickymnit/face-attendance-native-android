import { Inject, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { AttendanceDailySummary, AuditAction, ErpSyncState } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { DeviceAuthContext } from '../common/authenticated-request';
import { parseAttendanceDate } from '../common/date-utils';
import { PrismaService } from '../prisma/prisma.service';
import { ERP_ATTENDANCE_ADAPTER, ErpAttendanceAdapter } from './erp-attendance-adapter';

@Injectable()
export class ErpSyncService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(ErpSyncService.name);
  private timer: NodeJS.Timeout | null = null;

  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
    @Inject(ERP_ATTENDANCE_ADAPTER) private readonly adapter: ErpAttendanceAdapter,
  ) {}

  onModuleInit(): void {
    if (process.env.ERP_SYNC_WORKER_ENABLED === 'false') return;
    const intervalMillis = Number.parseInt(process.env.ERP_SYNC_INTERVAL_MS ?? '60000', 10);
    this.timer = setInterval(() => {
      void this.processPending().catch((error) => this.logger.warn(`ERP sync worker failed: ${(error as Error).message}`));
    }, Number.isFinite(intervalMillis) ? intervalMillis : 60000);
    this.timer.unref?.();
  }

  onModuleDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  async markSummaryPending(summary: AttendanceDailySummary): Promise<void> {
    await this.prisma.erpSyncStatus.upsert({
      where: {
        schoolId_erpStudentId_attendanceDate: {
          schoolId: summary.schoolId,
          erpStudentId: summary.erpStudentId,
          attendanceDate: summary.attendanceDate,
        },
      },
      create: {
        schoolId: summary.schoolId,
        erpStudentId: summary.erpStudentId,
        attendanceDate: summary.attendanceDate,
        status: ErpSyncState.PENDING,
        retryCount: 0,
      },
      update: {
        status: ErpSyncState.PENDING,
        lastError: null,
        syncedAt: null,
      },
    });
  }

  async processPending(limit = 50): Promise<{ attempted: number; synced: number; failed: number }> {
    const statuses = await this.prisma.erpSyncStatus.findMany({
      where: {
        status: { in: [ErpSyncState.PENDING, ErpSyncState.FAILED] },
      },
      orderBy: [{ lastTriedAt: 'asc' }, { createdAt: 'asc' }],
      take: limit,
    });

    let synced = 0;
    let failed = 0;
    for (const status of statuses) {
      if (!this.shouldRetry(status.retryCount, status.lastTriedAt)) continue;
      const summary = await this.prisma.attendanceDailySummary.findUnique({
        where: {
          schoolId_erpStudentId_attendanceDate: {
            schoolId: status.schoolId,
            erpStudentId: status.erpStudentId,
            attendanceDate: status.attendanceDate,
          },
        },
      });
      if (!summary) {
        failed += 1;
        await this.markFailed(status.id, 'Attendance summary not found');
        continue;
      }
      try {
        const result = await this.adapter.pushAttendanceSummary(summary);
        if (result.accepted || result.duplicate) {
          synced += 1;
          await this.markSynced(status.id, summary, result.erpReferenceId);
        } else {
          failed += 1;
          await this.markFailed(status.id, result.message ?? 'ERP rejected summary');
        }
      } catch (error) {
        failed += 1;
        await this.markFailed(status.id, (error as Error).message);
      }
    }

    return { attempted: statuses.length, synced, failed };
  }

  async retrySchool(schoolId: string, auth: DeviceAuthContext): Promise<{ attempted: number; synced: number; failed: number }> {
    if (schoolId !== auth.schoolId) throw new Error('School mismatch');
    await this.prisma.erpSyncStatus.updateMany({
      where: { schoolId, status: ErpSyncState.FAILED },
      data: { status: ErpSyncState.PENDING, lastError: null },
    });
    await this.auditService.log({
      action: AuditAction.ERP_SYNC_RETRY_REQUESTED,
      entityType: 'ErpSyncStatus',
      schoolId,
      deviceId: auth.deviceId,
    });
    return this.processPending();
  }

  async statusForSchool(schoolId: string, attendanceDate: string | undefined, auth: DeviceAuthContext) {
    if (schoolId !== auth.schoolId) throw new Error('School mismatch');
    const date = attendanceDate ? parseAttendanceDate(attendanceDate) : undefined;
    const where = { schoolId, ...(date ? { attendanceDate: date } : {}) };
    const [pending, synced, failed, last] = await Promise.all([
      this.prisma.erpSyncStatus.count({ where: { ...where, status: ErpSyncState.PENDING } }),
      this.prisma.erpSyncStatus.count({ where: { ...where, status: ErpSyncState.SYNCED } }),
      this.prisma.erpSyncStatus.count({ where: { ...where, status: ErpSyncState.FAILED } }),
      this.prisma.erpSyncStatus.findFirst({ where, orderBy: { lastTriedAt: 'desc' } }),
    ]);
    return { totalPending: pending, synced, failed, lastSyncTime: last?.lastTriedAt?.toISOString() ?? null };
  }

  private shouldRetry(retryCount: number, lastTriedAt: Date | null): boolean {
    if (!lastTriedAt) return true;
    const delayMillis = Math.min(60 * 60 * 1000, Math.pow(2, Math.min(retryCount, 8)) * 60 * 1000);
    return Date.now() - lastTriedAt.getTime() >= delayMillis;
  }

  private async markSynced(id: string, summary: AttendanceDailySummary, erpReferenceId?: string): Promise<void> {
    const now = new Date();
    await this.prisma.erpSyncStatus.update({
      where: { id },
      data: { status: ErpSyncState.SYNCED, lastTriedAt: now, syncedAt: now, lastError: null },
    });
    await this.auditService.log({
      action: AuditAction.ERP_SYNC_SUCCEEDED,
      entityType: 'AttendanceDailySummary',
      entityId: summary.id,
      schoolId: summary.schoolId,
      metadata: { erpStudentId: summary.erpStudentId, attendanceDate: summary.attendanceDate, erpReferenceId },
    });
  }

  private async markFailed(id: string, message: string): Promise<void> {
    await this.prisma.erpSyncStatus.update({
      where: { id },
      data: {
        status: ErpSyncState.FAILED,
        retryCount: { increment: 1 },
        lastError: message,
        lastTriedAt: new Date(),
      },
    });
    await this.auditService.log({
      action: AuditAction.ERP_SYNC_FAILED,
      entityType: 'ErpSyncStatus',
      entityId: id,
      metadata: { message },
    });
  }
}
