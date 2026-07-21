import { Injectable } from '@nestjs/common';
import { EmbeddingJobStatus, EmbeddingStatus, ImportJobItemStatus } from '@prisma/client';
import { createHash } from 'crypto';
import { PrismaService } from '../prisma/prisma.service';
import { EmbeddingWorkerClient, WorkerRequestError } from './embedding-worker.client';

export interface EmbeddingProcessResult {
  processed: boolean;
  success: boolean;
  errorCode?: string;
}

@Injectable()
export class EmbeddingJobProcessor {
  private readonly expectedModelVersion = process.env.AI_WORKER_EXPECTED_MODEL_VERSION ?? 'multipaz-sample-3f65d0c';

  constructor(
    private readonly prisma: PrismaService,
    private readonly workerClient: EmbeddingWorkerClient,
  ) {}

  isConfigured(): boolean {
    return this.workerClient.isConfigured();
  }

  async process(params: {
    jobId: string;
    importJobItemId: string;
    schoolId: string;
    erpStudentId: string;
    photoFileName: string;
    photoBuffer: Buffer;
  }): Promise<EmbeddingProcessResult> {
    if (!this.isConfigured()) {
      return { processed: false, success: false };
    }

    await this.prisma.embeddingJob.update({
      where: { id: params.jobId },
      data: { status: EmbeddingJobStatus.PROCESSING },
    });

    try {
      const result = await this.workerClient.generateEmbedding(params);
      if (result.status !== 'SUCCESS') {
        throw new WorkerRequestError(result.errorCode ?? 'EMBEDDING_FAILED', result.message ?? 'Embedding generation failed');
      }
      if (result.modelVersion !== this.expectedModelVersion) {
        throw new WorkerRequestError('MODEL_VERSION_MISMATCH', `Worker model ${result.modelVersion} does not match expected ${this.expectedModelVersion}`);
      }
      if (!result.embeddingBase64 || !result.embeddingSize || typeof result.qualityScore !== 'number') {
        throw new WorkerRequestError('INVALID_WORKER_RESPONSE', 'Worker response did not include embedding payload, size, and quality score');
      }
      const modelVersion = result.modelVersion;
      const embeddingSize = result.embeddingSize;
      const embeddingBase64 = result.embeddingBase64;
      const qualityScore = result.qualityScore;
      const checksum = createHash('sha256').update(embeddingBase64).digest('hex');
      await this.prisma.$transaction(async (tx) => {
        const school = await tx.school.update({
          where: { id: params.schoolId },
          data: { embeddingSyncVersion: { increment: 1 } },
          select: { embeddingSyncVersion: true },
        });
        await tx.faceEmbeddingMetadata.updateMany({
          where: {
            schoolId: params.schoolId,
            erpStudentId: params.erpStudentId,
            modelVersion,
            status: EmbeddingStatus.ACTIVE,
          },
          data: {
            status: EmbeddingStatus.DELETED,
            deletedAt: new Date(),
            embeddingSyncVersion: school.embeddingSyncVersion,
          },
        });
        await tx.faceEmbeddingMetadata.create({
          data: {
            schoolId: params.schoolId,
            erpStudentId: params.erpStudentId,
            modelVersion,
            embeddingSyncVersion: school.embeddingSyncVersion,
            embeddingSize,
            embeddingBase64,
            qualityScore,
            source: 'BULK_IMPORT',
            status: EmbeddingStatus.ACTIVE,
            checksum,
          },
        });
        await tx.importJobItem.update({
          where: { id: params.importJobItemId },
          data: { status: ImportJobItemStatus.EMBEDDING_DONE },
        });
        await tx.embeddingJob.update({
          where: { id: params.jobId },
          data: {
            status: EmbeddingJobStatus.COMPLETED,
            modelVersion,
            embeddingSize,
            qualityScore,
            completedAt: new Date(),
          },
        });
      });
      return { processed: true, success: true };
    } catch (error) {
      const code = error instanceof WorkerRequestError ? error.code : 'EMBEDDING_FAILED';
      await this.prisma.$transaction([
        this.prisma.importJobItem.update({
          where: { id: params.importJobItemId },
          data: { status: ImportJobItemStatus.EMBEDDING_FAILED, errorCode: code, errorMessage: (error as Error).message },
        }),
        this.prisma.embeddingJob.update({
          where: { id: params.jobId },
          data: { status: EmbeddingJobStatus.FAILED, errorCode: code, errorMessage: (error as Error).message, completedAt: new Date() },
        }),
      ]);
      return { processed: true, success: false, errorCode: code };
    }
  }
}
