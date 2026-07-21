import { BadRequestException, ForbiddenException, Injectable, NotFoundException, UnauthorizedException } from '@nestjs/common';
import { AuditAction, DeviceStatus, Prisma } from '@prisma/client';
import * as bcrypt from 'bcryptjs';
import { ConfigService } from '@nestjs/config';
import { randomUUID } from 'crypto';
import { AuditService } from '../audit/audit.service';
import { AuthService } from '../auth/auth.service';
import { DeviceAuthContext } from '../common/authenticated-request';
import { parseIsoDateTime } from '../common/date-utils';
import { PrismaService } from '../prisma/prisma.service';
import { DeviceHeartbeatDto } from './dto/heartbeat.dto';
import { RegisterDeviceDto } from './dto/register-device.dto';

@Injectable()
export class DevicesService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly configService: ConfigService,
    private readonly authService: AuthService,
    private readonly auditService: AuditService,
  ) {}

  async register(dto: RegisterDeviceDto) {
    const expectedSetupToken = this.configService.get<string>('DEVICE_SETUP_TOKEN', 'DEV-SETUP-TOKEN');
    if (dto.setupToken !== expectedSetupToken) {
      throw new UnauthorizedException('Invalid setup token');
    }

    const school = await this.prisma.school.findUnique({ where: { code: dto.schoolCode } });
    if (!school) throw new NotFoundException('School not found');

    const existing = await this.prisma.device.findFirst({
      where: { schoolId: school.id, gateId: dto.gateId, name: dto.deviceName },
    });
    const device = existing ?? await this.prisma.device.create({
      data: {
        id: `GATE-DEVICE-${randomUUID().slice(0, 8).toUpperCase()}`,
        schoolId: school.id,
        gateId: dto.gateId,
        name: dto.deviceName,
        deviceInfo: (dto.deviceInfo ?? {}) as Prisma.InputJsonValue,
        appVersion: typeof dto.deviceInfo?.appVersion === 'string' ? dto.deviceInfo.appVersion : undefined,
      },
    });

    const token = await this.authService.issueDeviceToken(device.id, school.id);
    await this.prisma.device.update({
      where: { id: device.id },
      data: { authTokenHash: await bcrypt.hash(token, 8), status: DeviceStatus.ACTIVE },
    });
    await this.auditService.log({
      action: AuditAction.DEVICE_REGISTERED,
      entityType: 'Device',
      entityId: device.id,
      schoolId: school.id,
      deviceId: device.id,
      metadata: { gateId: device.gateId, deviceName: device.name },
    });

    return {
      deviceId: device.id,
      schoolId: school.id,
      schoolCode: school.code,
      schoolName: school.name,
      gateId: device.gateId,
      deviceAccessToken: token,
      refreshToken: token,
      configVersion: device.configVersion,
      embeddingSyncVersion: device.embeddingSyncVersion,
      config: null,
      serverTime: new Date().toISOString(),
    };
  }

  async heartbeat(dto: DeviceHeartbeatDto, auth: DeviceAuthContext) {
    this.assertAuthMatches(dto.deviceId, dto.schoolId, auth);
    const timestamp = parseIsoDateTime(dto.timestamp, 'timestamp');
    const device = await this.prisma.device.update({
      where: { id: dto.deviceId },
      data: {
        lastHeartbeatAt: timestamp,
        appVersion: dto.appVersion,
      },
    });
    await this.auditService.log({
      action: AuditAction.DEVICE_HEARTBEAT,
      entityType: 'Device',
      entityId: device.id,
      schoolId: dto.schoolId,
      deviceId: device.id,
      metadata: {
        pendingAttendanceCount: dto.pendingAttendanceCount,
        pendingFailedRecognitionCount: dto.pendingFailedRecognitionCount,
        batteryPercent: dto.batteryPercent,
        isCharging: dto.isCharging,
        networkType: dto.networkType,
      },
    });

    return {
      accepted: true,
      serverTime: new Date().toISOString(),
      configVersion: device.configVersion,
      requiresConfigRefresh: false,
      requiresEmbeddingDeltaRefresh: false,
    };
  }

  async getConfig(deviceId: string, auth: DeviceAuthContext) {
    if (deviceId !== auth.deviceId) throw new ForbiddenException('Device mismatch');
    const device = await this.prisma.device.findUnique({
      where: { id: deviceId },
      include: { school: { include: { attendanceRule: true } } },
    });
    if (!device || device.schoolId !== auth.schoolId) throw new NotFoundException('Device not found');
    const rules = device.school.attendanceRule;
    return {
      deviceId: device.id,
      schoolId: device.schoolId,
      gateId: device.gateId,
      configVersion: device.configVersion,
      attendanceRules: {
        schoolStartTime: rules?.schoolStartTime ?? '08:00',
        lateAfterTime: rules?.lateAfterTime ?? '08:15',
        halfDayAfterTime: rules?.halfDayAfterTime ?? '10:30',
        duplicateScanCooldownMinutes: rules?.duplicateScanCooldownMinutes ?? 10,
        requireOutTime: rules?.requireOutTime ?? false,
        recognitionMode: rules?.recognitionMode ?? 'Strict',
      },
      syncRules: {
        attendanceBatchSize: 50,
        failedRecognitionBatchSize: 50,
        heartbeatIntervalMinutes: 15,
      },
      model: {
        modelVersion: 'multipaz-sample-3f65d0c',
        embeddingSize: 512,
        distanceMetric: 'COSINE',
      },
    };
  }

  private assertAuthMatches(deviceId: string, schoolId: string, auth: DeviceAuthContext): void {
    if (deviceId !== auth.deviceId || schoolId !== auth.schoolId) {
      throw new BadRequestException('Request device/school does not match bearer token');
    }
  }
}
