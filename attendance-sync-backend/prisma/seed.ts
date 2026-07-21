import 'dotenv/config';
import { PrismaClient, DeviceStatus, SchoolStatus, StudentStatus } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  await prisma.school.upsert({
    where: { id: 'school-demo' },
    update: {
      code: 'SCHOOL-DEMO',
      name: 'Schoollog Demo School',
      status: SchoolStatus.ACTIVE,
    },
    create: {
      id: 'school-demo',
      code: 'SCHOOL-DEMO',
      name: 'Schoollog Demo School',
      status: SchoolStatus.ACTIVE,
    },
  });

  await prisma.schoolAttendanceRule.upsert({
    where: { schoolId: 'school-demo' },
    update: {},
    create: {
      schoolId: 'school-demo',
      schoolStartTime: '08:00',
      lateAfterTime: '08:15',
      halfDayAfterTime: '10:30',
      duplicateScanCooldownMinutes: 10,
      recognitionMode: 'Strict',
    },
  });

  await prisma.device.upsert({
    where: { id: 'GATE-DEVICE-00042' },
    update: {
      schoolId: 'school-demo',
      gateId: 'main-gate',
      name: 'Main Gate Phone 1',
      status: DeviceStatus.ACTIVE,
    },
    create: {
      id: 'GATE-DEVICE-00042',
      schoolId: 'school-demo',
      gateId: 'main-gate',
      name: 'Main Gate Phone 1',
      status: DeviceStatus.ACTIVE,
      configVersion: 1,
      embeddingSyncVersion: 0,
      deviceInfo: { seeded: true },
      appVersion: '0.1.0',
    },
  });

  await prisma.student.upsert({
    where: { schoolId_erpStudentId: { schoolId: 'school-demo', erpStudentId: 'STU-2026-0001' } },
    update: {},
    create: {
      schoolId: 'school-demo',
      erpStudentId: 'STU-2026-0001',
      name: 'Demo Student',
      className: '10',
      section: 'A',
      rollNumber: '1',
      status: StudentStatus.ACTIVE,
    },
  });
}

main()
  .then(async () => prisma.$disconnect())
  .catch(async (error) => {
    console.error(error);
    await prisma.$disconnect();
    process.exit(1);
  });
