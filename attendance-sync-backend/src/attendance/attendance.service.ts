import { BadRequestException, Injectable } from '@nestjs/common';
import { AttendanceDailySummary, AttendanceEvent, AttendanceEventType, AuditAction, Prisma, SchoolAttendanceRule, SyncStatus } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { DeviceAuthContext } from '../common/authenticated-request';
import { parseAttendanceDate, parseIsoDateTime } from '../common/date-utils';
import { ErpSyncService } from '../erp-sync/erp-sync.service';
import { PrismaService } from '../prisma/prisma.service';
import { AttendanceEventSyncItemDto, SyncAttendanceEventsDto } from './dto/sync-attendance-events.dto';
import { FailedRecognitionSyncItemDto, SyncFailedRecognitionsDto } from './dto/sync-failed-recognitions.dto';

export type PerEventStatus = 'accepted' | 'duplicate' | 'rejected' | 'conflict';

export interface EventSyncResult {
  eventId: string;
  status: PerEventStatus;
  code?: string;
  message?: string;
  retryable?: boolean;
  erpReferenceId?: string;
}

@Injectable()
export class AttendanceService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly erpSyncService: ErpSyncService,
    private readonly auditService: AuditService,
  ) {}

  async syncEvents(dto: SyncAttendanceEventsDto, auth: DeviceAuthContext) {
    this.assertBatchAuth(dto.schoolId, dto.deviceId, auth);
    await this.verifyDevice(dto.schoolId, dto.deviceId);
    const rules = await this.prisma.schoolAttendanceRule.findUnique({ where: { schoolId: dto.schoolId } });

    const results: EventSyncResult[] = [];
    for (const event of dto.events) {
      results.push(await this.syncOneEvent(event, auth, rules).catch((error) => ({
        eventId: event.eventId,
        status: 'rejected' as const,
        code: error instanceof BadRequestException ? 'BAD_REQUEST' : 'SYNC_ERROR',
        message: (error as Error).message,
        retryable: false,
      })));
    }

    const accepted = results.filter((result) => result.status === 'accepted' || result.status === 'duplicate');
    const rejected = results.filter((result) => result.status === 'rejected' || result.status === 'conflict');

    await this.auditService.log({
      action: AuditAction.ATTENDANCE_SYNCED,
      entityType: 'AttendanceEvent',
      schoolId: auth.schoolId,
      deviceId: auth.deviceId,
      metadata: {
        acceptedCount: accepted.filter((result) => result.status === 'accepted').length,
        duplicateCount: accepted.filter((result) => result.status === 'duplicate').length,
        rejectedCount: rejected.length,
      },
    });

    return {
      results,
      accepted: accepted.map((result) => ({
        eventId: result.eventId,
        status: result.status === 'duplicate' ? 'duplicate/already_synced' : 'accepted',
        erpReferenceId: result.erpReferenceId,
      })),
      rejected: rejected.map((result) => ({
        eventId: result.eventId,
        status: result.status,
        code: result.code ?? 'REJECTED',
        message: result.message ?? 'Rejected',
        retryable: result.retryable ?? false,
      })),
      serverTime: new Date().toISOString(),
    };
  }

  async syncFailedRecognitions(dto: SyncFailedRecognitionsDto, auth: DeviceAuthContext) {
    this.assertBatchAuth(dto.schoolId, dto.deviceId, auth);
    await this.verifyDevice(dto.schoolId, dto.deviceId);
    const accepted: Array<{ failedRecognitionId: string; status: string }> = [];
    const rejected: Array<{ failedRecognitionId: string; status: string; code: string; message: string; retryable: boolean }> = [];

    for (const item of dto.failedRecognitions) {
      try {
        const schoolId = item.schoolId ?? dto.schoolId;
        const deviceId = item.deviceId ?? dto.deviceId;
        if (schoolId !== auth.schoolId || deviceId !== auth.deviceId) {
          throw new BadRequestException('Failed recognition device/school does not match bearer token');
        }
        await this.createFailedRecognitionLog(item, schoolId, deviceId, item.gateId ?? dto.gateId);
        accepted.push({ failedRecognitionId: item.failedRecognitionId, status: 'accepted' });
      } catch (error) {
        rejected.push({
          failedRecognitionId: item.failedRecognitionId,
          status: 'rejected',
          code: 'BAD_REQUEST',
          message: (error as Error).message,
          retryable: false,
        });
      }
    }

    await this.auditService.log({
      action: AuditAction.FAILED_RECOGNITION_SYNCED,
      entityType: 'FailedRecognitionLog',
      schoolId: auth.schoolId,
      deviceId: auth.deviceId,
      metadata: { acceptedCount: accepted.length, rejectedCount: rejected.length },
    });

    return { accepted, rejected, serverTime: new Date().toISOString() };
  }

  private async syncOneEvent(
    event: AttendanceEventSyncItemDto,
    auth: DeviceAuthContext,
    rules: SchoolAttendanceRule | null,
  ): Promise<EventSyncResult> {
    this.assertEventAuth(event, auth);
    const existing = await this.prisma.attendanceEvent.findUnique({
      where: { schoolId_eventId: { schoolId: event.schoolId, eventId: event.eventId } },
    });
    if (existing) {
      return { eventId: event.eventId, status: 'duplicate', message: 'duplicate/already_synced', retryable: false };
    }

    const { created, summary } = await this.createAttendanceEvent(event, rules);
    await this.erpSyncService.markSummaryPending(summary);
    return { eventId: event.eventId, status: 'accepted', erpReferenceId: created.id };
  }

  private async createAttendanceEvent(
    event: AttendanceEventSyncItemDto,
    rules: SchoolAttendanceRule | null,
  ): Promise<{ created: AttendanceEvent; summary: AttendanceDailySummary }> {
    const timestampLocal = parseIsoDateTime(event.timestamp, 'timestamp');
    const attendanceDate = event.attendanceDate ? parseAttendanceDate(event.attendanceDate) : this.dateOnlyUtc(timestampLocal);
    const erpStudentId = event.erpStudentId ?? event.studentId;
    const requestedEventType = event.eventType as AttendanceEventType;
    const storedEventType = requestedEventType === AttendanceEventType.OUT
      ? AttendanceEventType.OUT
      : this.statusFor(timestampLocal, rules);

    await this.prisma.student.upsert({
      where: { schoolId_erpStudentId: { schoolId: event.schoolId, erpStudentId } },
      create: {
        schoolId: event.schoolId,
        erpStudentId,
        name: erpStudentId,
        className: 'UNKNOWN',
      },
      update: {},
    });

    return this.prisma.$transaction(async (tx) => {
      const created = await tx.attendanceEvent.create({
        data: {
          eventId: event.eventId,
          schoolId: event.schoolId,
          erpStudentId,
          deviceId: event.deviceId,
          gateId: event.gateId,
          eventType: storedEventType,
          attendanceDate,
          timestampLocal,
          matchScore: event.matchScore,
          livenessScore: event.livenessScore,
          qualityScore: event.qualityScore,
          modelVersion: event.modelVersion,
          syncStatus: SyncStatus.ACCEPTED,
          rawPayload: event as unknown as Prisma.InputJsonValue,
        },
      });

      const summary = await this.applyDailySummary(tx, {
        schoolId: event.schoolId,
        erpStudentId,
        attendanceDate,
        timestampLocal,
        eventId: event.eventId,
        eventType: storedEventType,
      });

      return { created, summary };
    });
  }

  private async applyDailySummary(
    tx: Prisma.TransactionClient,
    event: {
      schoolId: string;
      erpStudentId: string;
      attendanceDate: Date;
      timestampLocal: Date;
      eventId: string;
      eventType: AttendanceEventType;
    },
  ): Promise<AttendanceDailySummary> {
    const existing = await tx.attendanceDailySummary.findUnique({
      where: {
        schoolId_erpStudentId_attendanceDate: {
          schoolId: event.schoolId,
          erpStudentId: event.erpStudentId,
          attendanceDate: event.attendanceDate,
        },
      },
    });

    if (!existing) {
      return tx.attendanceDailySummary.create({
        data: {
          schoolId: event.schoolId,
          erpStudentId: event.erpStudentId,
          attendanceDate: event.attendanceDate,
          status: event.eventType,
          firstEventId: event.eventId,
          firstSeenAt: event.timestampLocal,
          lastSeenAt: event.timestampLocal,
          inTime: event.eventType === AttendanceEventType.OUT ? null : event.timestampLocal,
          outTime: event.eventType === AttendanceEventType.OUT ? event.timestampLocal : null,
        },
      });
    }

    if (event.eventType === AttendanceEventType.OUT) {
      const shouldUpdateOut = !existing.outTime || event.timestampLocal > existing.outTime;
      return tx.attendanceDailySummary.update({
        where: { id: existing.id },
        data: {
          outTime: shouldUpdateOut ? event.timestampLocal : existing.outTime,
          lastSeenAt: event.timestampLocal > existing.lastSeenAt ? event.timestampLocal : existing.lastSeenAt,
          eventCount: { increment: 1 },
        },
      });
    }

    if (!existing.inTime || event.timestampLocal < existing.inTime) {
      return tx.attendanceDailySummary.update({
        where: { id: existing.id },
        data: {
          inTime: event.timestampLocal,
          firstSeenAt: event.timestampLocal,
          firstEventId: event.eventId,
          status: event.eventType,
        },
      });
    }
    return existing;
  }

  private async createFailedRecognitionLog(
    item: FailedRecognitionSyncItemDto,
    schoolId: string,
    deviceId: string,
    gateId: string,
  ): Promise<void> {
    await this.prisma.failedRecognitionLog.create({
      data: {
        schoolId,
        deviceId,
        gateId,
        reason: item.reason,
        timestamp: parseIsoDateTime(item.timestamp, 'timestamp'),
        qualityScore: item.qualityScore,
        livenessScore: item.livenessScore,
        modelVersion: item.modelVersion,
        syncStatus: SyncStatus.ACCEPTED,
        rawPayload: item as unknown as Prisma.InputJsonValue,
      },
    });
  }

  private async verifyDevice(schoolId: string, deviceId: string): Promise<void> {
    const device = await this.prisma.device.findFirst({ where: { id: deviceId, schoolId } });
    if (!device) throw new BadRequestException('Device does not belong to school');
  }

  private assertBatchAuth(schoolId: string, deviceId: string, auth: DeviceAuthContext): void {
    if (schoolId !== auth.schoolId || deviceId !== auth.deviceId) {
      throw new BadRequestException('Batch device/school does not match bearer token');
    }
  }

  private assertEventAuth(event: AttendanceEventSyncItemDto, auth: DeviceAuthContext): void {
    if (event.schoolId !== auth.schoolId || event.deviceId !== auth.deviceId) {
      throw new BadRequestException('Attendance event device/school does not match bearer token');
    }
  }

  private statusFor(timestamp: Date, rules: SchoolAttendanceRule | null): AttendanceEventType {
    const minutes = timestamp.getUTCHours() * 60 + timestamp.getUTCMinutes();
    const halfDayAfter = this.timeToMinutes(rules?.halfDayAfterTime ?? '10:30');
    const lateAfter = this.timeToMinutes(rules?.lateAfterTime ?? '08:15');
    if (minutes >= halfDayAfter) return AttendanceEventType.HALF_DAY;
    if (minutes >= lateAfter) return AttendanceEventType.LATE;
    return AttendanceEventType.PRESENT;
  }

  private timeToMinutes(value: string): number {
    const [hours, minutes] = value.split(':').map((part) => Number.parseInt(part, 10));
    if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return 0;
    return hours * 60 + minutes;
  }

  private dateOnlyUtc(date: Date): Date {
    return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  }
}
