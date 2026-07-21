import { AuditAction, AttendanceEventType, ErpSyncState } from '@prisma/client';
import { ErpSyncService } from './erp-sync.service';

const summary = {
  id: 'summary-1',
  schoolId: 'school-demo',
  erpStudentId: 'STU-1',
  attendanceDate: new Date('2026-07-20T00:00:00.000Z'),
  status: AttendanceEventType.PRESENT,
  firstEventId: 'event-1',
  firstSeenAt: new Date('2026-07-20T08:00:00.000Z'),
  lastSeenAt: new Date('2026-07-20T08:00:00.000Z'),
  inTime: new Date('2026-07-20T08:00:00.000Z'),
  outTime: null,
  eventCount: 1,
  createdAt: new Date(),
  updatedAt: new Date(),
};

function createPrismaMock(statuses: any[], summaries: any[]) {
  return {
    erpSyncStatus: {
      upsert: jest.fn(),
      findMany: jest.fn().mockResolvedValue(statuses),
      update: jest.fn(),
      updateMany: jest.fn(),
      count: jest.fn(),
      findFirst: jest.fn(),
    },
    attendanceDailySummary: {
      findUnique: jest.fn()
        .mockImplementation(async () => summaries.shift() ?? null),
    },
  };
}

describe('ErpSyncService', () => {
  beforeEach(() => {
    process.env.ERP_SYNC_WORKER_ENABLED = 'false';
  });

  it('marks pending ERP summary synced on adapter success', async () => {
    const prisma = createPrismaMock([
      { id: 'sync-1', schoolId: 'school-demo', erpStudentId: 'STU-1', attendanceDate: summary.attendanceDate, status: ErpSyncState.PENDING, retryCount: 0, lastTriedAt: null },
    ], [summary]);
    const adapter = { pushAttendanceSummary: jest.fn().mockResolvedValue({ accepted: true, erpReferenceId: 'ERP-1' }) };
    const audit = { log: jest.fn() };
    const service = new ErpSyncService(prisma as any, audit as any, adapter as any);

    const result = await service.processPending();

    expect(result).toEqual({ attempted: 1, synced: 1, failed: 0 });
    expect(prisma.erpSyncStatus.update).toHaveBeenCalledWith(expect.objectContaining({
      where: { id: 'sync-1' },
      data: expect.objectContaining({ status: ErpSyncState.SYNCED, lastError: null }),
    }));
    expect(audit.log).toHaveBeenCalledWith(expect.objectContaining({ action: AuditAction.ERP_SYNC_SUCCEEDED }));
  });

  it('marks ERP sync failed when adapter is down', async () => {
    const prisma = createPrismaMock([
      { id: 'sync-1', schoolId: 'school-demo', erpStudentId: 'STU-1', attendanceDate: summary.attendanceDate, status: ErpSyncState.PENDING, retryCount: 0, lastTriedAt: null },
    ], [summary]);
    const adapter = { pushAttendanceSummary: jest.fn().mockRejectedValue(new Error('ERP unavailable')) };
    const audit = { log: jest.fn() };
    const service = new ErpSyncService(prisma as any, audit as any, adapter as any);

    const result = await service.processPending();

    expect(result).toEqual({ attempted: 1, synced: 0, failed: 1 });
    expect(prisma.erpSyncStatus.update).toHaveBeenCalledWith(expect.objectContaining({
      where: { id: 'sync-1' },
      data: expect.objectContaining({ status: ErpSyncState.FAILED, retryCount: { increment: 1 }, lastError: 'ERP unavailable' }),
    }));
  });

  it('treats duplicate ERP push as synced for idempotent retry', async () => {
    const prisma = createPrismaMock([
      { id: 'sync-1', schoolId: 'school-demo', erpStudentId: 'STU-1', attendanceDate: summary.attendanceDate, status: ErpSyncState.FAILED, retryCount: 1, lastTriedAt: null },
    ], [summary]);
    const adapter = { pushAttendanceSummary: jest.fn().mockResolvedValue({ accepted: false, duplicate: true }) };
    const service = new ErpSyncService(prisma as any, { log: jest.fn() } as any, adapter as any);

    const result = await service.processPending();

    expect(result.synced).toBe(1);
    expect(prisma.erpSyncStatus.update).toHaveBeenCalledWith(expect.objectContaining({
      data: expect.objectContaining({ status: ErpSyncState.SYNCED }),
    }));
  });

  it('continues after partial batch failure', async () => {
    const second = { ...summary, id: 'summary-2', erpStudentId: 'STU-2' };
    const prisma = createPrismaMock([
      { id: 'sync-1', schoolId: 'school-demo', erpStudentId: 'STU-1', attendanceDate: summary.attendanceDate, status: ErpSyncState.PENDING, retryCount: 0, lastTriedAt: null },
      { id: 'sync-2', schoolId: 'school-demo', erpStudentId: 'STU-2', attendanceDate: summary.attendanceDate, status: ErpSyncState.PENDING, retryCount: 0, lastTriedAt: null },
    ], [summary, second]);
    const adapter = {
      pushAttendanceSummary: jest.fn()
        .mockResolvedValueOnce({ accepted: true })
        .mockResolvedValueOnce({ accepted: false, message: 'ERP row rejected' }),
    };
    const service = new ErpSyncService(prisma as any, { log: jest.fn() } as any, adapter as any);

    const result = await service.processPending();

    expect(result).toEqual({ attempted: 2, synced: 1, failed: 1 });
    expect(prisma.erpSyncStatus.update).toHaveBeenCalledTimes(2);
  });
});
