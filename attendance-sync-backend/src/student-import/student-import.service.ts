import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { AuditAction, EmbeddingJobStatus, ImportJobItemStatus, ImportJobStatus, Prisma, StudentStatus } from '@prisma/client';
import * as path from 'path';
import { AuditService } from '../audit/audit.service';
import { DeviceAuthContext } from '../common/authenticated-request';
import { PrismaService } from '../prisma/prisma.service';
import { EmbeddingJobProcessor } from './embedding-job.processor';

interface UploadedMemoryFile {
  originalname?: string;
  buffer?: Buffer;
}

interface CsvStudentRow {
  rowNumber: number;
  erpStudentId: string;
  name: string;
  className: string;
  section: string;
  rollNumber: string;
  photoFileName: string;
}

interface ImportItemDraft {
  rowNumber: number;
  erpStudentId?: string;
  name?: string;
  className?: string;
  section?: string;
  rollNumber?: string;
  photoFileName?: string;
  status: ImportJobItemStatus;
  errorCode?: string;
  errorMessage?: string;
}

@Injectable()
export class StudentImportService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
    private readonly embeddingJobProcessor: EmbeddingJobProcessor,
  ) {}

  async importStudents(
    schoolId: string,
    csvFile: UploadedMemoryFile,
    zipFile: UploadedMemoryFile,
    auth: DeviceAuthContext,
  ) {
    this.assertSchoolAuth(schoolId, auth);
    if (!csvFile.buffer || !zipFile.buffer) {
      throw new BadRequestException('Uploaded files must be memory-backed');
    }
    const school = await this.prisma.school.findUnique({ where: { id: schoolId }, select: { id: true } });
    if (!school) throw new NotFoundException('School not found');

    const job = await this.prisma.importJob.create({
      data: {
        schoolId,
        status: ImportJobStatus.PROCESSING,
        sourceFileName: csvFile.originalname ?? 'students.csv',
        zipFileName: zipFile.originalname ?? 'photos.zip',
      },
    });

    const failJob = async (code: string, message: string) => {
      await this.prisma.importJobItem.createMany({
        data: [{ importJobId: job.id, schoolId, rowNumber: 0, status: ImportJobItemStatus.FAILED, errorCode: code, errorMessage: message }],
      });
      const failed = await this.prisma.importJob.update({
        where: { id: job.id },
        data: { status: ImportJobStatus.FAILED, totalRows: 0, validRows: 0, failedRows: 1, completedAt: new Date() },
      });
      return this.toSummary(failed);
    };

    let rows: CsvStudentRow[];
    let zipEntries: Set<string>;
    try {
      rows = parseStudentCsv(csvFile.buffer.toString('utf8'));
    } catch (error) {
      return failJob('INVALID_CSV', (error as Error).message);
    }
    try {
      zipEntries = listZipEntries(zipFile.buffer);
    } catch (error) {
      return failJob('BAD_ZIP', (error as Error).message);
    }

    const seen = new Set<string>();
    const zipFiles = extractZipFiles(zipFile.buffer);
    let studentsCreated = 0;
    let studentsUpdated = 0;
    let embeddingsCreated = 0;
    let failedEmbeddings = 0;
    const itemStatuses: ImportJobItemStatus[] = [];

    for (const row of rows) {
      const base = {
        rowNumber: row.rowNumber,
        erpStudentId: row.erpStudentId,
        name: row.name,
        className: row.className,
        section: row.section,
        rollNumber: row.rollNumber,
        photoFileName: row.photoFileName,
      };
      const validationError = validateRow(row, seen, zipEntries);
      if (validationError) {
        await this.prisma.importJobItem.create({
          data: { importJobId: job.id, schoolId, ...base, status: ImportJobItemStatus.FAILED, ...validationError },
        });
        itemStatuses.push(ImportJobItemStatus.FAILED);
        continue;
      }
      seen.add(row.erpStudentId);
      const existingStudent = await this.prisma.student.findUnique({
        where: { schoolId_erpStudentId: { schoolId, erpStudentId: row.erpStudentId } },
        select: { id: true },
      });
      await this.prisma.student.upsert({
        where: { schoolId_erpStudentId: { schoolId, erpStudentId: row.erpStudentId } },
        update: {
          name: row.name,
          className: row.className,
          section: row.section || undefined,
          rollNumber: row.rollNumber || undefined,
          status: StudentStatus.ACTIVE,
        },
        create: {
          schoolId,
          erpStudentId: row.erpStudentId,
          name: row.name,
          className: row.className,
          section: row.section || undefined,
          rollNumber: row.rollNumber || undefined,
          status: StudentStatus.ACTIVE,
        },
      });
      if (existingStudent) studentsUpdated += 1; else studentsCreated += 1;

      const importItem = await this.prisma.importJobItem.create({
        data: { importJobId: job.id, schoolId, ...base, status: ImportJobItemStatus.EMBEDDING_PENDING },
      });
      const embeddingJob = await this.prisma.embeddingJob.create({
        data: {
          schoolId,
          erpStudentId: row.erpStudentId,
          importJobItemId: importItem.id,
          status: EmbeddingJobStatus.PENDING,
        },
      });
      const photoBuffer = zipFiles.get(normalizeZipName(row.photoFileName));
      const processResult = photoBuffer
        ? await this.embeddingJobProcessor.process({
            jobId: embeddingJob.id,
            importJobItemId: importItem.id,
            schoolId,
            erpStudentId: row.erpStudentId,
            photoFileName: row.photoFileName,
            photoBuffer,
          })
        : { processed: false, success: false };
      if (processResult.processed && processResult.success) {
        embeddingsCreated += 1;
        itemStatuses.push(ImportJobItemStatus.EMBEDDING_DONE);
      } else if (processResult.processed && !processResult.success) {
        failedEmbeddings += 1;
        itemStatuses.push(ImportJobItemStatus.EMBEDDING_FAILED);
      } else {
        itemStatuses.push(ImportJobItemStatus.EMBEDDING_PENDING);
      }
    }

    const failedRows = itemStatuses.filter((status) => status === ImportJobItemStatus.FAILED).length;
    const validRows = rows.length - failedRows;
    const completed = await this.prisma.importJob.update({
      where: { id: job.id },
      data: {
        totalRows: rows.length,
        validRows,
        failedRows,
        studentsCreated,
        studentsUpdated,
        embeddingsCreated,
        failedEmbeddings,
        status: failedRows > 0 || failedEmbeddings > 0 ? ImportJobStatus.COMPLETED_WITH_ERRORS : ImportJobStatus.COMPLETED,
        completedAt: new Date(),
      },
    });

    await this.auditService.log({
      action: AuditAction.STUDENT_IMPORT_CREATED,
      entityType: 'ImportJob',
      entityId: job.id,
      schoolId,
      deviceId: auth.deviceId,
      metadata: { totalRows: rows.length, validRows, failedRows, studentsCreated, studentsUpdated, embeddingsCreated, failedEmbeddings } as Prisma.InputJsonValue,
    });

    return this.toSummary(completed);
  }

  async getStatus(schoolId: string, importId: string, auth: DeviceAuthContext) {
    this.assertSchoolAuth(schoolId, auth);
    const job = await this.prisma.importJob.findFirst({ where: { id: importId, schoolId } });
    if (!job) throw new NotFoundException('Import job not found');
    return this.toSummary(job);
  }

  async getErrors(schoolId: string, importId: string, auth: DeviceAuthContext) {
    this.assertSchoolAuth(schoolId, auth);
    const job = await this.prisma.importJob.findFirst({ where: { id: importId, schoolId }, select: { id: true } });
    if (!job) throw new NotFoundException('Import job not found');
    const errors = await this.prisma.importJobItem.findMany({
      where: { importJobId: importId, status: ImportJobItemStatus.FAILED },
      orderBy: { rowNumber: 'asc' },
    });
    return {
      importId,
      errors: errors.map((item) => ({
        rowNumber: item.rowNumber,
        erpStudentId: item.erpStudentId,
        photoFileName: item.photoFileName,
        code: item.errorCode,
        message: item.errorMessage,
      })),
    };
  }

  private assertSchoolAuth(schoolId: string, auth: DeviceAuthContext): void {
    if (auth.schoolId !== schoolId) {
      throw new ForbiddenException('Device is not authorized for this school');
    }
  }

  private toSummary(job: {
    id: string;
    schoolId: string;
    status: ImportJobStatus;
    totalRows: number;
    validRows: number;
    failedRows: number;
    studentsCreated?: number;
    studentsUpdated?: number;
    embeddingsCreated?: number;
    failedEmbeddings?: number;
    createdAt: Date;
    completedAt: Date | null;
  }) {
    return {
      importId: job.id,
      schoolId: job.schoolId,
      status: job.status,
      totalRows: job.totalRows,
      validRows: job.validRows,
      failedRows: job.failedRows,
      studentsCreated: job.studentsCreated ?? 0,
      studentsUpdated: job.studentsUpdated ?? 0,
      embeddingsCreated: job.embeddingsCreated ?? 0,
      failedEmbeddings: job.failedEmbeddings ?? 0,
      createdAt: job.createdAt.toISOString(),
      completedAt: job.completedAt?.toISOString() ?? null,
    };
  }
}

function validateRow(row: CsvStudentRow, seen: Set<string>, zipEntries: Set<string>): { errorCode: string; errorMessage: string } | null {
  if (!row.erpStudentId) return { errorCode: 'MISSING_STUDENT_ID', errorMessage: 'erpStudentId is required' };
  if (!row.name) return { errorCode: 'MISSING_NAME', errorMessage: 'name is required' };
  if (!row.className) return { errorCode: 'MISSING_CLASS', errorMessage: 'className is required' };
  if (!row.photoFileName) return { errorCode: 'MISSING_PHOTO_FILE_NAME', errorMessage: 'photoFileName is required' };
  if (seen.has(row.erpStudentId)) return { errorCode: 'DUPLICATE_STUDENT_ID', errorMessage: `Duplicate erpStudentId ${row.erpStudentId} in CSV` };
  if (!zipEntries.has(normalizeZipName(row.photoFileName))) return { errorCode: 'MISSING_PHOTO', errorMessage: `Photo ${row.photoFileName} not found in ZIP` };
  return null;
}


function extractZipFiles(buffer: Buffer): Map<string, Buffer> {
  const eocdOffset = findEndOfCentralDirectory(buffer);
  if (eocdOffset < 0) throw new Error('ZIP end of central directory not found');
  const entryCount = buffer.readUInt16LE(eocdOffset + 10);
  let cursor = buffer.readUInt32LE(eocdOffset + 16);
  const files = new Map<string, Buffer>();
  for (let index = 0; index < entryCount; index += 1) {
    if (cursor + 46 > buffer.length || buffer.readUInt32LE(cursor) !== 0x02014b50) {
      throw new Error('ZIP central directory is invalid');
    }
    const compressionMethod = buffer.readUInt16LE(cursor + 10);
    const compressedSize = buffer.readUInt32LE(cursor + 20);
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const localHeaderOffset = buffer.readUInt32LE(cursor + 42);
    const name = buffer.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8');
    if (name && !name.endsWith('/')) {
      if (buffer.readUInt32LE(localHeaderOffset) !== 0x04034b50) {
        throw new Error('ZIP local file header is invalid');
      }
      const localNameLength = buffer.readUInt16LE(localHeaderOffset + 26);
      const localExtraLength = buffer.readUInt16LE(localHeaderOffset + 28);
      const dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength;
      const compressed = buffer.subarray(dataStart, dataStart + compressedSize);
      let data: Buffer;
      if (compressionMethod === 0) {
        data = Buffer.from(compressed);
      } else if (compressionMethod === 8) {
        data = require('zlib').inflateRawSync(compressed) as Buffer;
      } else {
        throw new Error(`ZIP compression method ${compressionMethod} is not supported`);
      }
      const normalized = normalizeZipName(name);
      files.set(normalized, data);
      files.set(normalizeZipName(path.basename(name)), data);
    }
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

function parseStudentCsv(input: string): CsvStudentRow[] {
  const table = parseCsv(input.replace(/^﻿/, ''));
  if (table.length < 2) throw new Error('CSV must contain a header row and at least one student row');
  const headers = table[0].map((header) => header.trim());
  const missing = RequiredHeaders.filter((header) => !headers.includes(header));
  if (missing.length > 0) throw new Error(`CSV missing required column(s): ${missing.join(', ')}`);
  const rows: CsvStudentRow[] = [];
  table.slice(1).forEach((values, index) => {
    if (values.every((value) => value.trim() === '')) return;
    const record = Object.fromEntries(headers.map((header, headerIndex) => [header, (values[headerIndex] ?? '').trim()]));
    rows.push({
      rowNumber: index + 2,
      erpStudentId: record.erpStudentId,
      name: record.name,
      className: record.className,
      section: record.section,
      rollNumber: record.rollNumber,
      photoFileName: record.photoFileName,
    });
  });
  if (rows.length === 0) throw new Error('CSV does not contain student rows');
  return rows;
}

function parseCsv(input: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let inQuotes = false;
  for (let index = 0; index < input.length; index += 1) {
    const char = input[index];
    const next = input[index + 1];
    if (char === '"') {
      if (inQuotes && next === '"') {
        cell += '"';
        index += 1;
      } else {
        inQuotes = !inQuotes;
      }
      continue;
    }
    if (char === ',' && !inQuotes) {
      row.push(cell);
      cell = '';
      continue;
    }
    if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && next === '\n') index += 1;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
      continue;
    }
    cell += char;
  }
  if (inQuotes) throw new Error('CSV has an unclosed quoted value');
  row.push(cell);
  rows.push(row);
  return rows.filter((values) => values.some((value) => value.trim() !== ''));
}

export function listZipEntries(buffer: Buffer): Set<string> {
  const eocdOffset = findEndOfCentralDirectory(buffer);
  if (eocdOffset < 0) throw new Error('ZIP end of central directory not found');
  const entryCount = buffer.readUInt16LE(eocdOffset + 10);
  let cursor = buffer.readUInt32LE(eocdOffset + 16);
  const entries = new Set<string>();
  for (let index = 0; index < entryCount; index += 1) {
    if (cursor + 46 > buffer.length || buffer.readUInt32LE(cursor) !== 0x02014b50) {
      throw new Error('ZIP central directory is invalid');
    }
    const nameLength = buffer.readUInt16LE(cursor + 28);
    const extraLength = buffer.readUInt16LE(cursor + 30);
    const commentLength = buffer.readUInt16LE(cursor + 32);
    const name = buffer.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8');
    if (name && !name.endsWith('/')) {
      entries.add(normalizeZipName(name));
      entries.add(normalizeZipName(path.basename(name)));
    }
    cursor += 46 + nameLength + extraLength + commentLength;
  }
  if (entries.size === 0) throw new Error('ZIP does not contain files');
  return entries;
}

function findEndOfCentralDirectory(buffer: Buffer): number {
  const minimum = Math.max(0, buffer.length - 65_557);
  for (let index = buffer.length - 22; index >= minimum; index -= 1) {
    if (buffer.readUInt32LE(index) === 0x06054b50) return index;
  }
  return -1;
}

function normalizeZipName(name: string): string {
  return name.replace(/\\/g, '/').split('/').filter(Boolean).join('/').trim();
}

const RequiredHeaders = ['erpStudentId', 'name', 'className', 'section', 'rollNumber', 'photoFileName'];
