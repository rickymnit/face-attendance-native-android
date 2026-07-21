import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { EmbeddingStatus, StudentStatus } from '@prisma/client';
import { EmbeddingsService } from './embeddings.service';

const modelVersion = 'multipaz-sample-3f65d0c';
const student = {
  name: 'Student One',
  className: '10',
  section: 'A',
  rollNumber: '1',
  status: StudentStatus.ACTIVE,
};

function embedding(overrides: any = {}) {
  return {
    id: 'embedding-1',
    schoolId: 'school-demo',
    erpStudentId: 'STU-1',
    modelVersion,
    embeddingSyncVersion: 1,
    embeddingSize: 512,
    embeddingBase64: 'AAAA',
    qualityScore: 0.91,
    source: 'BULK_IMPORT',
    status: EmbeddingStatus.ACTIVE,
    checksum: 'checksum-1',
    createdAt: new Date('2026-07-21T08:00:00.000Z'),
    updatedAt: new Date('2026-07-21T08:00:00.000Z'),
    deletedAt: null,
    student,
    ...overrides,
  };
}

function createPrismaMock(rows: any[], schoolVersion = 1) {
  return {
    school: { findUnique: jest.fn().mockResolvedValue({ embeddingSyncVersion: schoolVersion }) },
    faceEmbeddingMetadata: { findMany: jest.fn().mockResolvedValue(rows) },
  };
}

function serviceWith(rows: any[], schoolVersion = 1) {
  const prisma = createPrismaMock(rows, schoolVersion);
  const service = new EmbeddingsService(prisma as any, { log: jest.fn() } as any);
  return { service, prisma };
}

const auth = { schoolId: 'school-demo', deviceId: 'device-1' };

describe('EmbeddingsService', () => {
  it('returns first full sync embeddings', async () => {
    const { service } = serviceWith([embedding()], 1);
    const result = await service.delta('school-demo', '0', auth, modelVersion);

    expect(result.fromVersion).toBe(0);
    expect(result.toVersion).toBe(1);
    expect(result.embeddings).toHaveLength(1);
    expect(result.embeddings[0]).toMatchObject({ erpStudentId: 'STU-1', checksum: 'checksum-1' });
  });

  it('returns only changes after local version', async () => {
    const row = embedding({ erpStudentId: 'STU-2', embeddingSyncVersion: 2, checksum: 'checksum-2' });
    const { service, prisma } = serviceWith([row], 2);
    const result = await service.delta('school-demo', '1', auth, modelVersion);

    expect(prisma.faceEmbeddingMetadata.findMany).toHaveBeenCalledWith(expect.objectContaining({
      where: expect.objectContaining({ embeddingSyncVersion: { gt: 1 } }),
    }));
    expect(result.embeddings[0].erpStudentId).toBe('STU-2');
    expect(result.toVersion).toBe(2);
  });

  it('returns re-enrollment as an updated active embedding without deleting the same student', async () => {
    const deleted = embedding({ status: EmbeddingStatus.DELETED, embeddingSyncVersion: 3, deletedAt: new Date('2026-07-21T09:00:00.000Z'), embeddingBase64: null });
    const active = embedding({ id: 'embedding-2', embeddingSyncVersion: 3, embeddingBase64: 'BBBB', checksum: 'checksum-new' });
    const { service } = serviceWith([deleted, active], 3);
    const result = await service.delta('school-demo', '2', auth, modelVersion);

    expect(result.embeddings).toHaveLength(1);
    expect(result.embeddings[0].embeddingBase64).toBe('BBBB');
    expect(result.deletedEmbeddings).toHaveLength(0);
  });

  it('returns deleted embeddings', async () => {
    const row = embedding({ status: EmbeddingStatus.DELETED, embeddingBase64: null, deletedAt: new Date('2026-07-21T09:00:00.000Z') });
    const { service } = serviceWith([row], 1);
    const result = await service.delta('school-demo', '0', auth, modelVersion);

    expect(result.embeddings).toHaveLength(0);
    expect(result.deletedEmbeddings).toEqual([expect.objectContaining({ erpStudentId: 'STU-1', modelVersion })]);
  });

  it('blocks wrong school access', async () => {
    const { service } = serviceWith([]);
    await expect(service.delta('other-school', '0', auth, modelVersion)).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('rejects unsupported model version', async () => {
    const { service } = serviceWith([]);
    await expect(service.delta('school-demo', '0', auth, 'other-model')).rejects.toBeInstanceOf(BadRequestException);
  });
});
