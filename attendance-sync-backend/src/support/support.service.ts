import { Injectable } from '@nestjs/common';
import { AuditAction, AttendanceEventType, ErpSyncState } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class SupportService {
  constructor(private readonly prisma: PrismaService) {}

  async schoolsHealth() {
    const schools = await this.prisma.school.findMany({
      orderBy: { name: 'asc' },
      include: { devices: true },
    });
    return Promise.all(schools.map(async (school) => {
      const onlineDevices = school.devices.filter((device) => this.isOnline(device.lastHeartbeatAt)).length;
      const [pendingErpSync, failedRecognitionCount] = await Promise.all([
        this.prisma.erpSyncStatus.count({ where: { schoolId: school.id, status: { in: [ErpSyncState.PENDING, ErpSyncState.FAILED] } } }),
        this.prisma.failedRecognitionLog.count({ where: { schoolId: school.id, timestamp: { gte: this.todayUtc() } } }),
      ]);
      return {
        schoolId: school.id,
        schoolName: school.name,
        totalDevices: school.devices.length,
        onlineDevices,
        offlineDevices: school.devices.length - onlineDevices,
        pendingErpSync,
        failedRecognitionCount,
        embeddingSyncVersion: school.embeddingSyncVersion,
      };
    }));
  }

  async devicesForSchool(schoolId: string) {
    const devices = await this.prisma.device.findMany({ where: { schoolId }, orderBy: { gateId: 'asc' } });
    return Promise.all(devices.map(async (device) => {
      const latestHeartbeat = await this.latestAuditMetadata(schoolId, device.id, AuditAction.DEVICE_HEARTBEAT);
      return {
        deviceId: device.id,
        gateId: device.gateId,
        name: device.name,
        status: device.status,
        online: this.isOnline(device.lastHeartbeatAt),
        lastHeartbeat: device.lastHeartbeatAt?.toISOString() ?? null,
        appVersion: device.appVersion,
        modelVersion: latestHeartbeat?.modelVersion ?? null,
        pendingAttendanceSync: latestHeartbeat?.pendingAttendanceCount ?? null,
        pendingFailedRecognitionSync: latestHeartbeat?.pendingFailedRecognitionCount ?? null,
        embeddingSyncVersion: device.embeddingSyncVersion,
      };
    }));
  }

  async todaySummary(schoolId: string) {
    const today = this.todayUtc();
    const summaries = await this.prisma.attendanceDailySummary.findMany({
      where: { schoolId, attendanceDate: today },
      include: { student: true },
      orderBy: { firstSeenAt: 'asc' },
    });
    const counts = { present: 0, late: 0, halfDay: 0, out: 0 };
    for (const summary of summaries) {
      if (summary.status === AttendanceEventType.PRESENT) counts.present += 1;
      if (summary.status === AttendanceEventType.LATE) counts.late += 1;
      if (summary.status === AttendanceEventType.HALF_DAY) counts.halfDay += 1;
      if (summary.status === AttendanceEventType.OUT) counts.out += 1;
    }
    return {
      schoolId,
      attendanceDate: today.toISOString().slice(0, 10),
      totalMarked: summaries.length,
      ...counts,
      recent: summaries.slice(-25).map((summary) => ({
        erpStudentId: summary.erpStudentId,
        name: summary.student?.name ?? summary.erpStudentId,
        className: summary.student?.className ?? null,
        status: summary.status,
        inTime: summary.inTime?.toISOString() ?? null,
        outTime: summary.outTime?.toISOString() ?? null,
      })),
    };
  }

  async failedRecognitions(schoolId: string) {
    const logs = await this.prisma.failedRecognitionLog.findMany({
      where: { schoolId, timestamp: { gte: this.todayUtc() } },
      orderBy: { timestamp: 'desc' },
      take: 100,
    });
    return {
      schoolId,
      total: logs.length,
      livenessFailureCount: logs.filter((log) => log.reason.toLowerCase().includes('liveness')).length,
      logs: logs.map((log) => ({
        id: log.id,
        deviceId: log.deviceId,
        gateId: log.gateId,
        reason: log.reason,
        timestamp: log.timestamp.toISOString(),
        qualityScore: log.qualityScore,
        livenessScore: log.livenessScore,
        modelVersion: log.modelVersion,
      })),
    };
  }

  async syncHealth(schoolId: string) {
    const [pendingErp, syncedErp, failedErp, failedRecognitionCount, lastAttendance] = await Promise.all([
      this.prisma.erpSyncStatus.count({ where: { schoolId, status: ErpSyncState.PENDING } }),
      this.prisma.erpSyncStatus.count({ where: { schoolId, status: ErpSyncState.SYNCED } }),
      this.prisma.erpSyncStatus.count({ where: { schoolId, status: ErpSyncState.FAILED } }),
      this.prisma.failedRecognitionLog.count({ where: { schoolId, syncStatus: 'ACCEPTED' } }),
      this.prisma.attendanceEvent.findFirst({ where: { schoolId }, orderBy: { timestampServer: 'desc' } }),
    ]);
    return {
      schoolId,
      pendingErpSync: pendingErp,
      syncedErpSync: syncedErp,
      failedErpSync: failedErp,
      failedRecognitionCount,
      lastAttendanceSync: lastAttendance?.timestampServer.toISOString() ?? null,
    };
  }

  async offlineDevices() {
    const cutoff = new Date(Date.now() - 30 * 60 * 1000);
    const devices = await this.prisma.device.findMany({
      where: { OR: [{ lastHeartbeatAt: null }, { lastHeartbeatAt: { lt: cutoff } }] },
      include: { school: true },
      orderBy: { lastHeartbeatAt: 'asc' },
      take: 200,
    });
    return devices.map((device) => ({
      schoolId: device.schoolId,
      schoolName: device.school.name,
      deviceId: device.id,
      gateId: device.gateId,
      name: device.name,
      lastHeartbeat: device.lastHeartbeatAt?.toISOString() ?? null,
      appVersion: device.appVersion,
    }));
  }

  async logsSummary(deviceId: string) {
    const logs = await this.prisma.auditLog.findMany({
      where: { deviceId },
      orderBy: { createdAt: 'desc' },
      take: 500,
    });
    const counts = logs.reduce<Record<string, number>>((acc, log) => {
      acc[log.action] = (acc[log.action] ?? 0) + 1;
      return acc;
    }, {});
    return {
      deviceId,
      totalLogs: logs.length,
      counts,
      lastLogAt: logs[0]?.createdAt.toISOString() ?? null,
      recent: logs.slice(0, 25).map((log) => ({
        action: log.action,
        entityType: log.entityType,
        entityId: log.entityId,
        createdAt: log.createdAt.toISOString(),
      })),
    };
  }

  private isOnline(lastHeartbeatAt: Date | null): boolean {
    return !!lastHeartbeatAt && Date.now() - lastHeartbeatAt.getTime() <= 15 * 60 * 1000;
  }

  private todayUtc(): Date {
    const now = new Date();
    return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  }

  private async latestAuditMetadata(schoolId: string, deviceId: string, action: AuditAction): Promise<Record<string, any> | null> {
    const log = await this.prisma.auditLog.findFirst({
      where: { schoolId, deviceId, action },
      orderBy: { createdAt: 'desc' },
    });
    return (log?.metadata as Record<string, any> | null) ?? null;
  }
}
