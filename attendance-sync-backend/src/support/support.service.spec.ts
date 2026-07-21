import { AttendanceEventType, DeviceStatus, ErpSyncState } from '@prisma/client';
import { SupportService } from './support.service';

describe('SupportService', () => {
  it('summarizes school health without exposing raw embeddings or images', async () => {
    const prisma = {
      school: { findMany: jest.fn().mockResolvedValue([{ id: 'school-1', name: 'Demo', embeddingSyncVersion: 7, devices: [{ lastHeartbeatAt: new Date() }, { lastHeartbeatAt: null }] }]) },
      erpSyncStatus: { count: jest.fn().mockResolvedValue(2) },
      failedRecognitionLog: { count: jest.fn().mockResolvedValue(3) },
    };
    const service = new SupportService(prisma as any);

    const result = await service.schoolsHealth();

    expect(result[0]).toEqual(expect.objectContaining({ schoolId: 'school-1', onlineDevices: 1, offlineDevices: 1, pendingErpSync: 2, failedRecognitionCount: 3 }));
    expect(JSON.stringify(result)).not.toContain('embeddingBase64');
  });

  it('returns today attendance summary counts', async () => {
    const prisma = {
      attendanceDailySummary: { findMany: jest.fn().mockResolvedValue([
        { erpStudentId: 'S1', status: AttendanceEventType.PRESENT, student: { name: 'A', className: '1' }, inTime: new Date(), outTime: null },
        { erpStudentId: 'S2', status: AttendanceEventType.LATE, student: null, inTime: new Date(), outTime: null },
      ]) },
    };
    const service = new SupportService(prisma as any);

    const result = await service.todaySummary('school-1');

    expect(result.totalMarked).toBe(2);
    expect(result.present).toBe(1);
    expect(result.late).toBe(1);
  });

  it('returns sync health counts for support dashboard', async () => {
    const prisma = {
      erpSyncStatus: { count: jest.fn() },
      failedRecognitionLog: { count: jest.fn().mockResolvedValue(4) },
      attendanceEvent: { findFirst: jest.fn().mockResolvedValue({ timestampServer: new Date('2026-07-20T08:00:00.000Z') }) },
    };
    prisma.erpSyncStatus.count
      .mockResolvedValueOnce(5)
      .mockResolvedValueOnce(10)
      .mockResolvedValueOnce(1);
    const service = new SupportService(prisma as any);

    const result = await service.syncHealth('school-1');

    expect(result).toEqual(expect.objectContaining({ pendingErpSync: 5, syncedErpSync: 10, failedErpSync: 1, failedRecognitionCount: 4 }));
    expect(prisma.erpSyncStatus.count).toHaveBeenCalledWith({ where: { schoolId: 'school-1', status: ErpSyncState.PENDING } });
  });

  it('lists offline devices', async () => {
    const prisma = {
      device: { findMany: jest.fn().mockResolvedValue([{ id: 'D1', schoolId: 'school-1', gateId: 'main', name: 'Gate', status: DeviceStatus.ACTIVE, lastHeartbeatAt: null, appVersion: '0.1.0', school: { name: 'Demo' } }]) },
    };
    const service = new SupportService(prisma as any);

    const result = await service.offlineDevices();

    expect(result[0]).toEqual(expect.objectContaining({ deviceId: 'D1', schoolName: 'Demo', lastHeartbeat: null }));
  });
});
