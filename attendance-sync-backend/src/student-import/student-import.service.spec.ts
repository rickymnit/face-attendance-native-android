import { EmbeddingJobStatus, ImportJobItemStatus, ImportJobStatus } from '@prisma/client';
import { StudentImportService } from './student-import.service';

function createPrismaMock() {
  const createdJob = {
    id: 'import-1',
    schoolId: 'school-demo',
    status: ImportJobStatus.PROCESSING,
    totalRows: 0,
    validRows: 0,
    failedRows: 0,
    studentsCreated: 0,
    studentsUpdated: 0,
    embeddingsCreated: 0,
    failedEmbeddings: 0,
    createdAt: new Date('2026-07-21T00:00:00.000Z'),
    completedAt: null,
  };
  return {
    school: { findUnique: jest.fn().mockResolvedValue({ id: 'school-demo' }) },
    importJob: {
      create: jest.fn().mockResolvedValue(createdJob),
      update: jest.fn(async ({ data }: any) => ({ ...createdJob, ...data })),
      findFirst: jest.fn().mockResolvedValue(createdJob),
    },
    importJobItem: {
      create: jest.fn(async ({ data }: any) => ({ id: `item-${data.rowNumber}`, ...data })),
      createMany: jest.fn().mockResolvedValue({ count: 1 }),
      findMany: jest.fn().mockResolvedValue([]),
    },
    embeddingJob: {
      create: jest.fn(async ({ data }: any) => ({ id: `embedding-${data.erpStudentId}`, ...data })),
      update: jest.fn(),
    },
    student: {
      findUnique: jest.fn().mockResolvedValue(null),
      upsert: jest.fn().mockResolvedValue({ id: 'student-1' }),
    },
  };
}

function createProcessorMock(result: { processed: boolean; success: boolean; errorCode?: string } = { processed: false, success: false }) {
  return {
    process: jest.fn().mockResolvedValue(result),
  };
}

function serviceWith(prisma = createPrismaMock(), processor = createProcessorMock()) {
  return {
    prisma,
    processor,
    service: new StudentImportService(prisma as any, { log: jest.fn() } as any, processor as any),
  };
}

function file(originalname: string, buffer: Buffer) {
  return { originalname, buffer };
}

function csv(rows: string): Buffer {
  return Buffer.from(rows, 'utf8');
}

function zipWith(fileNames: string[]): Buffer {
  const localParts: Buffer[] = [];
  const centralParts: Buffer[] = [];
  let offset = 0;
  for (const fileName of fileNames) {
    const name = Buffer.from(fileName, 'utf8');
    const data = Buffer.from('photo', 'utf8');
    const local = Buffer.alloc(30 + name.length + data.length);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt32LE(0, 10);
    local.writeUInt32LE(0, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    name.copy(local, 30);
    data.copy(local, 30 + name.length);
    localParts.push(local);

    const central = Buffer.alloc(46 + name.length);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt32LE(0, 12);
    central.writeUInt32LE(0, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt16LE(0, 30);
    central.writeUInt16LE(0, 32);
    central.writeUInt16LE(0, 34);
    central.writeUInt16LE(0, 36);
    central.writeUInt32LE(0, 38);
    central.writeUInt32LE(offset, 42);
    name.copy(central, 46);
    centralParts.push(central);
    offset += local.length;
  }
  const centralOffset = offset;
  const centralDirectory = Buffer.concat(centralParts);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(fileNames.length, 8);
  eocd.writeUInt16LE(fileNames.length, 10);
  eocd.writeUInt32LE(centralDirectory.length, 12);
  eocd.writeUInt32LE(centralOffset, 16);
  eocd.writeUInt16LE(0, 20);
  return Buffer.concat([...localParts, centralDirectory, eocd]);
}

const auth = { schoolId: 'school-demo', deviceId: 'device-1' };
const header = 'erpStudentId,name,className,section,rollNumber,photoFileName\n';

describe('StudentImportService', () => {
  it('imports a valid CSV and creates a pending embedding job when worker is not configured', async () => {
    const { service, prisma, processor } = serviceWith();
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv(`${header}STU-1,Asha,10,A,1,STU-1.jpg\n`)),
      file('photos.zip', zipWith(['STU-1.jpg'])),
      auth,
    );

    expect(result.status).toBe(ImportJobStatus.COMPLETED);
    expect(result.validRows).toBe(1);
    expect(result.studentsCreated).toBe(1);
    expect(prisma.student.upsert).toHaveBeenCalledTimes(1);
    expect(prisma.importJobItem.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ status: ImportJobItemStatus.EMBEDDING_PENDING }),
    }));
    expect(prisma.embeddingJob.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ status: EmbeddingJobStatus.PENDING }),
    }));
    expect(processor.process).toHaveBeenCalledTimes(1);
  });

  it('updates embedding counts when worker processing succeeds', async () => {
    const { service } = serviceWith(createPrismaMock(), createProcessorMock({ processed: true, success: true }));
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv(`${header}STU-1,Asha,10,A,1,STU-1.jpg\n`)),
      file('photos.zip', zipWith(['STU-1.jpg'])),
      auth,
    );

    expect(result.embeddingsCreated).toBe(1);
    expect(result.failedEmbeddings).toBe(0);
  });

  it('records embedding failure when worker processing fails', async () => {
    const { service } = serviceWith(createPrismaMock(), createProcessorMock({ processed: true, success: false, errorCode: 'NO_FACE' }));
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv(`${header}STU-1,Asha,10,A,1,STU-1.jpg\n`)),
      file('photos.zip', zipWith(['STU-1.jpg'])),
      auth,
    );

    expect(result.status).toBe(ImportJobStatus.COMPLETED_WITH_ERRORS);
    expect(result.failedEmbeddings).toBe(1);
  });

  it('records a row error when a referenced photo is missing', async () => {
    const { service, prisma } = serviceWith();
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv(`${header}STU-1,Asha,10,A,1,missing.jpg\n`)),
      file('photos.zip', zipWith(['other.jpg'])),
      auth,
    );

    expect(result.status).toBe(ImportJobStatus.COMPLETED_WITH_ERRORS);
    expect(result.failedRows).toBe(1);
    expect(prisma.student.upsert).not.toHaveBeenCalled();
    expect(prisma.importJobItem.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ errorCode: 'MISSING_PHOTO' }),
    }));
  });

  it('records duplicate ERP student IDs inside one CSV', async () => {
    const { service, prisma } = serviceWith();
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv(`${header}STU-1,Asha,10,A,1,STU-1.jpg\nSTU-1,Asha Again,10,A,2,STU-1b.jpg\n`)),
      file('photos.zip', zipWith(['STU-1.jpg', 'STU-1b.jpg'])),
      auth,
    );

    expect(result.status).toBe(ImportJobStatus.COMPLETED_WITH_ERRORS);
    expect(result.validRows).toBe(1);
    expect(result.failedRows).toBe(1);
    expect(prisma.importJobItem.create).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ errorCode: 'DUPLICATE_STUDENT_ID' }),
    }));
  });

  it('creates a failed import job for invalid CSV', async () => {
    const { service, prisma } = serviceWith();
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv('erpStudentId,name\nSTU-1,Asha\n')),
      file('photos.zip', zipWith(['STU-1.jpg'])),
      auth,
    );

    expect(result.status).toBe(ImportJobStatus.FAILED);
    expect(prisma.importJobItem.createMany).toHaveBeenCalledWith(expect.objectContaining({
      data: [expect.objectContaining({ errorCode: 'INVALID_CSV' })],
    }));
  });

  it('creates a failed import job for a bad ZIP', async () => {
    const { service, prisma } = serviceWith();
    const result = await service.importStudents(
      'school-demo',
      file('students.csv', csv(`${header}STU-1,Asha,10,A,1,STU-1.jpg\n`)),
      file('photos.zip', Buffer.from('not a zip')),
      auth,
    );

    expect(result.status).toBe(ImportJobStatus.FAILED);
    expect(prisma.importJobItem.createMany).toHaveBeenCalledWith(expect.objectContaining({
      data: [expect.objectContaining({ errorCode: 'BAD_ZIP' })],
    }));
  });
});
