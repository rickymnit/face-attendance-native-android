import { BadRequestException, ForbiddenException, Injectable } from '@nestjs/common';
import { AuditAction, EmbeddingStatus } from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { DeviceAuthContext } from '../common/authenticated-request';
import { PrismaService } from '../prisma/prisma.service';

const DefaultModelVersion = 'multipaz-sample-3f65d0c';
const DefaultLimit = 500;
const MaxLimit = 1000;

@Injectable()
export class EmbeddingsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  async delta(
    schoolId: string,
    sinceVersion: string | undefined,
    auth: DeviceAuthContext,
    modelVersion = DefaultModelVersion,
    limitValue?: string,
  ) {
    if (schoolId !== auth.schoolId) {
      throw new ForbiddenException('Requested school does not match bearer token');
    }
    if (modelVersion !== DefaultModelVersion) {
      throw new BadRequestException('MODEL_VERSION_UNSUPPORTED');
    }
    const fromVersion = this.parseNonNegativeInt(sinceVersion ?? '0', 'sinceVersion');
    const limit = Math.min(this.parseNonNegativeInt(limitValue ?? String(DefaultLimit), 'limit'), MaxLimit);
    const school = await this.prisma.school.findUnique({
      where: { id: schoolId },
      select: { embeddingSyncVersion: true },
    });
    if (!school) throw new BadRequestException('School not found');

    const changed = await this.prisma.faceEmbeddingMetadata.findMany({
      where: {
        schoolId,
        modelVersion,
        embeddingSyncVersion: { gt: fromVersion },
      },
      include: { student: true },
      orderBy: [{ embeddingSyncVersion: 'asc' }, { updatedAt: 'asc' }],
      take: limit + 1,
    });
    const hasMore = changed.length > limit;
    const page = changed.slice(0, limit);
    const pageToVersion = page.at(-1)?.embeddingSyncVersion ?? fromVersion;
    const activeStudentKeys = new Set(
      page
        .filter((embedding) => embedding.status === EmbeddingStatus.ACTIVE)
        .map((embedding) => `${embedding.schoolId}:${embedding.erpStudentId}:${embedding.modelVersion}`),
    );

    const embeddings = page
      .filter((embedding) => embedding.status === EmbeddingStatus.ACTIVE && embedding.embeddingBase64)
      .map((embedding) => ({
        erpStudentId: embedding.erpStudentId,
        changeType: 'UPSERT',
        student: {
          name: embedding.student?.name ?? embedding.erpStudentId,
          className: embedding.student?.className ?? 'UNKNOWN',
          section: embedding.student?.section ?? '',
          rollNumber: embedding.student?.rollNumber ?? '',
          status: embedding.student?.status ?? 'ACTIVE',
        },
        embeddingBase64: embedding.embeddingBase64!,
        embeddingSize: embedding.embeddingSize,
        qualityScore: embedding.qualityScore ?? 0,
        status: embedding.status,
        updatedAt: embedding.updatedAt.toISOString(),
        checksum: embedding.checksum,
        embeddingSyncVersion: embedding.embeddingSyncVersion,
      }));

    const deletedEmbeddings = page
      .filter((embedding) => embedding.status === EmbeddingStatus.DELETED)
      .filter((embedding) => !activeStudentKeys.has(`${embedding.schoolId}:${embedding.erpStudentId}:${embedding.modelVersion}`))
      .map((embedding) => ({
        erpStudentId: embedding.erpStudentId,
        modelVersion: embedding.modelVersion,
        deletedAt: (embedding.deletedAt ?? embedding.updatedAt).toISOString(),
        checksum: embedding.checksum,
        embeddingSyncVersion: embedding.embeddingSyncVersion,
      }));

    await this.auditService.log({
      action: AuditAction.EMBEDDING_DELTA_FETCHED,
      entityType: 'FaceEmbeddingMetadata',
      schoolId,
      deviceId: auth.deviceId,
      metadata: { sinceVersion: fromVersion, toVersion: pageToVersion, hasMore, modelVersion },
    });

    return {
      schoolId,
      modelVersion,
      fromVersion,
      toVersion: hasMore ? pageToVersion : school.embeddingSyncVersion,
      newSyncVersion: hasMore ? pageToVersion : school.embeddingSyncVersion,
      hasMore,
      embeddings,
      deletedEmbeddings,
    };
  }

  private parseNonNegativeInt(value: string, name: string): number {
    const parsed = Number.parseInt(value, 10);
    if (!Number.isFinite(parsed) || parsed < 0) {
      throw new BadRequestException(`${name} must be a non-negative integer`);
    }
    return parsed;
  }
}
