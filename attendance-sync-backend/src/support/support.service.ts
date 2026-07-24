import { Injectable } from '@nestjs/common';
import { AuditAction, AttendanceEventType, ErpSyncState } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class SupportService {
  constructor(private readonly prisma: PrismaService) {}

  async schoolsHealth() {
    const schools = await this.prisma.school.findMany({
      orderBy: { name: 'asc' },
      include: { devices: { include: { health: true } } },
    });
    return Promise.all(schools.map(async (school) => {
      const onlineDevices = school.devices.filter((device) => this.isOnline(device.lastHeartbeatAt)).length;
      const deviceHealthStatuses = school.devices.map((device) => this.healthForDevice(device));
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
        healthyDevices: deviceHealthStatuses.filter((health) => health.status === 'HEALTHY').length,
        warningDevices: deviceHealthStatuses.filter((health) => health.status === 'WARNING').length,
        criticalDevices: deviceHealthStatuses.filter((health) => health.status === 'CRITICAL').length,
      };
    }));
  }

  async devicesForSchool(schoolId: string) {
    const devices = await this.prisma.device.findMany({
      where: { schoolId },
      include: { health: true },
      orderBy: { gateId: 'asc' },
    });
    return devices.map((device) => {
      const health = this.healthForDevice(device);
      return {
        deviceId: device.id,
        gateId: device.gateId,
        name: device.name,
        status: device.status,
        online: this.isOnline(device.lastHeartbeatAt),
        healthStatus: health.status,
        healthReason: health.reason,
        lastHeartbeat: device.lastHeartbeatAt?.toISOString() ?? null,
        appVersion: device.health?.appVersion ?? device.appVersion,
        modelVersion: device.health?.modelVersion ?? null,
        embeddingCount: device.health?.embeddingCount ?? null,
        pendingAttendanceSync: device.health?.pendingAttendanceCount ?? null,
        pendingFailedRecognitionSync: device.health?.pendingFailedRecognitionCount ?? null,
        lastAttendanceSyncAt: device.health?.lastAttendanceSyncAt?.toISOString() ?? null,
        lastEmbeddingSyncAt: device.health?.lastEmbeddingSyncAt?.toISOString() ?? null,
        batteryPercent: device.health?.batteryPercent ?? null,
        isCharging: device.health?.isCharging ?? null,
        networkStatus: device.health?.networkStatus ?? null,
        cameraStatus: device.health?.cameraStatus ?? null,
        averageDecisionTime: device.health?.averageDecisionTime ?? null,
        lastError: device.health?.lastError ?? null,
        embeddingSyncVersion: device.embeddingSyncVersion,
      };
    });
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
      include: { school: true, health: true },
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
      appVersion: device.health?.appVersion ?? device.appVersion,
      healthStatus: this.healthForDevice(device).status,
      healthReason: this.healthForDevice(device).reason,
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

  private healthForDevice(device: { lastHeartbeatAt: Date | null; health?: any }): { status: 'HEALTHY' | 'WARNING' | 'CRITICAL'; reason: string } {
    if (!this.isOnline(device.lastHeartbeatAt)) {
      return { status: 'CRITICAL', reason: 'Device is offline or heartbeat is stale' };
    }

    const health = device.health;
    if (!health) {
      return { status: 'WARNING', reason: 'No health telemetry received yet' };
    }

    if (health.lastError) {
      return { status: 'CRITICAL', reason: `Last device error: ${health.lastError}` };
    }

    if (health.networkStatus === 'OFFLINE') {
      return { status: 'CRITICAL', reason: 'Device reports offline network status' };
    }

    if (typeof health.batteryPercent === 'number' && health.batteryPercent <= 15 && health.isCharging === false) {
      return { status: 'CRITICAL', reason: 'Battery low and charger is disconnected' };
    }

    if ((health.pendingAttendanceCount ?? 0) >= 100) {
      return { status: 'WARNING', reason: 'Large pending attendance sync queue' };
    }

    if ((health.embeddingCount ?? 0) <= 0) {
      return { status: 'WARNING', reason: 'No active embeddings loaded on device' };
    }

    if (typeof health.averageDecisionTime === 'number' && health.averageDecisionTime > 3000) {
      return { status: 'WARNING', reason: 'Average decision time is above 3 seconds' };
    }

    const cameraStatus = String(health.cameraStatus ?? '').toLowerCase();
    if (cameraStatus.includes('unavailable') || cameraStatus.includes('error')) {
      return { status: 'CRITICAL', reason: `Camera status: ${health.cameraStatus}` };
    }

    return { status: 'HEALTHY', reason: 'Heartbeat and telemetry are within expected limits' };
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
