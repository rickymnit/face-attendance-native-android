import { Injectable } from '@nestjs/common';

export interface WorkerEmbeddingResponse {
  status: string;
  studentId: string;
  schoolId: string;
  embedding?: number[];
  embeddingBase64?: string;
  qualityScore?: number;
  modelVersion?: string;
  embeddingSize?: number;
  errorCode?: string;
  message?: string;
}

@Injectable()
export class EmbeddingWorkerClient {
  private readonly baseUrl = process.env.AI_WORKER_BASE_URL?.replace(/\/$/, '') ?? null;

  isConfigured(): boolean {
    return Boolean(this.baseUrl);
  }

  async generateEmbedding(params: {
    schoolId: string;
    erpStudentId: string;
    photoFileName: string;
    photoBuffer: Buffer;
  }): Promise<WorkerEmbeddingResponse> {
    if (!this.baseUrl) {
      throw new Error('AI worker is not configured');
    }
    const form = new FormData();
    form.append('schoolId', params.schoolId);
    form.append('studentId', params.erpStudentId);
    form.append('image', new Blob([params.photoBuffer as BlobPart]), params.photoFileName);
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}/embedding/generate`, { method: 'POST', body: form });
    } catch (error) {
      throw new WorkerRequestError('WORKER_UNAVAILABLE', (error as Error).message);
    }
    const body = await response.json().catch(() => null) as WorkerEmbeddingResponse | null;
    if (!response.ok || !body) {
      throw new WorkerRequestError(body?.errorCode ?? 'WORKER_ERROR', body?.message ?? `AI worker HTTP ${response.status}`);
    }
    return body;
  }
}

export class WorkerRequestError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}
