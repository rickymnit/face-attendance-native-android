import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { AuditAction, EnrollmentStatus, Prisma } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { DeviceAuthContext } from '../common/authenticated-request';
import { PrismaService } from '../prisma/prisma.service';
import { CreateEnrollmentRequestDto } from './dto/create-enrollment-request.dto';

@Injectable()
export class EnrollmentService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  async create(dto: CreateEnrollmentRequestDto, auth: DeviceAuthContext) {
    if (dto.schoolId !== auth.schoolId || dto.deviceId !== auth.deviceId) {
      throw new BadRequestException('Enrollment request device/school does not match bearer token');
    }

    await this.prisma.student.upsert({
      where: { schoolId_erpStudentId: { schoolId: dto.schoolId, erpStudentId: dto.erpStudentId } },
      create: {
        schoolId: dto.schoolId,
        erpStudentId: dto.erpStudentId,
        name: dto.studentName ?? dto.erpStudentId,
        className: dto.className ?? 'UNKNOWN',
        section: dto.section,
        rollNumber: dto.rollNumber,
      },
      update: {
        name: dto.studentName,
        className: dto.className,
        section: dto.section,
        rollNumber: dto.rollNumber,
      },
    });

    const request = await this.prisma.enrollmentRequest.create({
      data: {
        schoolId: dto.schoolId,
        erpStudentId: dto.erpStudentId,
        deviceId: dto.deviceId,
        gateId: dto.gateId,
        studentName: dto.studentName,
        className: dto.className,
        section: dto.section,
        rollNumber: dto.rollNumber,
        modelVersion: dto.modelVersion,
        qualityScore: dto.qualityScore,
        status: EnrollmentStatus.PENDING,
        rawPayload: dto as unknown as Prisma.InputJsonValue,
      },
    });

    await this.auditService.log({
      action: AuditAction.ENROLLMENT_REQUESTED,
      entityType: 'EnrollmentRequest',
      entityId: request.id,
      schoolId: dto.schoolId,
      deviceId: dto.deviceId,
      metadata: { erpStudentId: dto.erpStudentId, status: request.status },
    });

    return {
      id: request.id,
      status: request.status,
      serverTime: new Date().toISOString(),
    };
  }

  async get(id: string, auth: DeviceAuthContext) {
    const request = await this.prisma.enrollmentRequest.findUnique({ where: { id } });
    if (!request || request.schoolId !== auth.schoolId) throw new NotFoundException('Enrollment request not found');
    return request;
  }
}
