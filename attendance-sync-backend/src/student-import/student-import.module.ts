import { Module } from '@nestjs/common';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { StudentImportController } from './student-import.controller';
import { EmbeddingJobProcessor } from './embedding-job.processor';
import { EmbeddingWorkerClient } from './embedding-worker.client';
import { StudentImportService } from './student-import.service';

@Module({
  imports: [AuthModule, AuditModule],
  controllers: [StudentImportController],
  providers: [EmbeddingWorkerClient, EmbeddingJobProcessor, StudentImportService],
  exports: [StudentImportService],
})
export class StudentImportModule {}
