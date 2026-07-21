import { AttendanceEventType, SyncStatus } from '@prisma/client';
import { AttendanceService } from './attendance.service';

function createPrismaMock() {
  const attendanceEvent = { findUnique: jest.fn(), create: jest.fn() };
  const student = { upsert: jest.fn() };
  const attendanceDailySummary = {
    findUnique: jest.fn(),
    create: jest.fn(async (args: any) => ({ id: 'summary-created', ...args.data })),
    update: jest.fn(async (args: any) => ({ id: args.where.id, ...args.data })),
  };
  return {
    attendanceEvent,
    student,
    attendanceDailySummary,
    failedRecognitionLog: { create: jest.fn() },
    schoolAttendanceRule: { findUnique: jest.fn().mockResolvedValue({ lateAfterTime: '08:15', halfDayAfterTime: '10:30' }) },
    device: { findFirst: jest.fn().mockResolvedValue({ id: 'GATE-DEVICE-00042', schoolId: 'school-demo' }) },
    $transaction: jest.fn(async (callback: (tx: any) => unknown) => callback({ attendanceEvent, attendanceDailySummary })),
  };
}

const baseEvent = {
  eventId: 'event-1',
  schoolId: 'school-demo',
  studentId: 'STU-2026-0001',
  deviceId: 'GATE-DEVICE-00042',
  gateId: 'main-gate',
  eventType: 'PRESENT' as const,
  attendanceDate: '2026-07-20',
  timestamp: Date.parse('2026-07-20T08:00:00.000Z'),
  matchScore: 0.93,
  livenessScore: 0.88,
  qualityScore: 0.9,
  modelVersion: 'facenet512-v1',
};

describe('AttendanceService', () => {
  it('returns duplicate/already_synced when the same event is sent twice', async () => {
    const prisma = createPrismaMock();
    prisma.attendanceEvent.findUnique.mockResolvedValueOnce({ eventId: 'event-1' });
    const erpSync = { markSummaryPending: jest.fn() };
    const service = new AttendanceService(prisma as any, erpSync as any, { log: jest.fn() } as any);

    const result = await service.syncEvents(
      { deviceId: 'GATE-DEVICE-00042', schoolId: 'school-demo', gateId: 'main-gate', events: [baseEvent] },
      { deviceId: 'GATE-DEVICE-00042', schoolId: 'school-demo' },
    );

    expect(result.accepted).toEqual([{ eventId: 'event-1', status: 'duplicate/already_synced', erpReferenceId: undefined }]);
    expect(prisma.attendanceEvent.create).not.toHaveBeenCalled();
    expect(erpSync.markSummaryPending).not.toHaveBeenCalled();
  });

  it('saves event from a second gate but ignores duplicate IN in daily summary', async () => {
    const prisma = createPrismaMock();
    prisma.attendanceEvent.findUnique.mockResolvedValueOnce(null);
    prisma.attendanceEvent.create.mockResolvedValueOnce({ id: 'db-event-2', eventId: 'event-2', eventType: AttendanceEventType.PRESENT, syncStatus: SyncStatus.ACCEPTED });
    prisma.attendanceDailySummary.findUnique.mockResolvedValueOnce({
      id: 'summary-1',
      inTime: new Date('2026-07-20T08:00:00.000Z'),
      outTime: null,
      firstSeenAt: new Date('2026-07-20T08:00:00.000Z'),
      lastSeenAt: new Date('2026-07-20T08:00:00.000Z'),
    });
    const service = new AttendanceService(prisma as any, { markSummaryPending: jest.fn() } as any, { log: jest.fn() } as any);

    const result = await service.syncEvents(
      {
        deviceId: 'GATE-DEVICE-00042',
        schoolId: 'school-demo',
        gateId: 'side-gate',
        events: [{ ...baseEvent, eventId: 'event-2', gateId: 'side-gate', timestamp: Date.parse('2026-07-20T08:02:00.000Z') }],
      },
      { deviceId: 'GATE-DEVICE-00042', schoolId: 'school-demo' },
    );

    expect(result.accepted[0].status).toBe('accepted');
    expect(prisma.attendanceEvent.create).toHaveBeenCalledTimes(1);
    expect(prisma.attendanceDailySummary.update).not.toHaveBeenCalled();
  });

  it('updates inTime when an older offline IN event arrives later', async () => {
    const prisma = createPrismaMock();
    prisma.attendanceEvent.findUnique.mockResolvedValueOnce(null);
    prisma.attendanceEvent.create.mockResolvedValueOnce({ id: 'db-event-3', eventId: 'event-3', eventType: AttendanceEventType.PRESENT, syncStatus: SyncStatus.ACCEPTED });
    prisma.attendanceDailySummary.findUnique.mockResolvedValueOnce({
      id: 'summary-1',
      inTime: new Date('2026-07-20T08:10:00.000Z'),
      outTime: null,
      firstSeenAt: new Date('2026-07-20T08:10:00.000Z'),
      lastSeenAt: new Date('2026-07-20T08:10:00.000Z'),
    });
    const service = new AttendanceService(prisma as any, { markSummaryPending: jest.fn() } as any, { log: jest.fn() } as any);

    await service.syncEvents(
      { deviceId: 'GATE-DEVICE-00042', schoolId: 'school-demo', gateId: 'main-gate', events: [{ ...baseEvent, eventId: 'event-3', timestamp: Date.parse('2026-07-20T07:55:00.000Z') }] },
      { deviceId: 'GATE-DEVICE-00042', schoolId: 'school-demo' },
    );

    expect(prisma.attendanceDailySummary.update).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'summary-1' },
        data: expect.objectContaining({ firstEventId: 'event-3', status: AttendanceEventType.PRESENT }),
      }),
    );
  });

  it('rejects invalid device/school sync before processing events', async () => {
    const prisma = createPrismaMock();
    prisma.device.findFirst.mockResolvedValueOnce(null);
    const service = new AttendanceService(prisma as any, { markSummaryPending: jest.fn() } as any, { log: jest.fn() } as any);

    await expect(service.syncEvents(
      { deviceId: 'BAD-DEVICE', schoolId: 'school-demo', gateId: 'main-gate', events: [baseEvent] },
      { deviceId: 'BAD-DEVICE', schoolId: 'school-demo' },
    )).rejects.toThrow('Device does not belong to school');
  });
});
