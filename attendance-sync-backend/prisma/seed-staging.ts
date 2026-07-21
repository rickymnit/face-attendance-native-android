import 'dotenv/config';
import { PrismaClient, DeviceStatus, SchoolStatus, StudentStatus } from '@prisma/client';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  const schoolId = 'school-staging-local';
  const deviceId = 'GATE-STAGING-0001';
  const gateId = 'main-gate';
  const jwtSecret = process.env.JWT_SECRET ?? 'dev-only-secret';
  const jwtExpiresIn = process.env.JWT_EXPIRES_IN ?? '30d';
  const jwtService = new JwtService({ secret: jwtSecret, signOptions: { expiresIn: jwtExpiresIn } });
  const token = await jwtService.signAsync({ sub: deviceId, deviceId, schoolId });

  await prisma.school.upsert({
    where: { id: schoolId },
    update: { code: 'LOCAL-STAGING', name: 'Local Staging School', status: SchoolStatus.ACTIVE },
    create: { id: schoolId, code: 'LOCAL-STAGING', name: 'Local Staging School', status: SchoolStatus.ACTIVE },
  });

  await prisma.schoolAttendanceRule.upsert({
    where: { schoolId },
    update: {
      schoolStartTime: '08:00',
      lateAfterTime: '08:15',
      halfDayAfterTime: '10:30',
      duplicateScanCooldownMinutes: 10,
      recognitionMode: 'Strict',
      configVersion: 1,
    },
    create: {
      schoolId,
      schoolStartTime: '08:00',
      lateAfterTime: '08:15',
      halfDayAfterTime: '10:30',
      duplicateScanCooldownMinutes: 10,
      recognitionMode: 'Strict',
      configVersion: 1,
    },
  });

  await prisma.device.upsert({
    where: { id: deviceId },
    update: {
      schoolId,
      gateId,
      name: 'Local Staging Gate Phone',
      status: DeviceStatus.ACTIVE,
      authTokenHash: await bcrypt.hash(token, 8),
      configVersion: 1,
      embeddingSyncVersion: 0,
      appVersion: '0.1.0',
    },
    create: {
      id: deviceId,
      schoolId,
      gateId,
      name: 'Local Staging Gate Phone',
      status: DeviceStatus.ACTIVE,
      authTokenHash: await bcrypt.hash(token, 8),
      configVersion: 1,
      embeddingSyncVersion: 0,
      deviceInfo: { seed: 'local-staging' },
      appVersion: '0.1.0',
    },
  });

  for (let index = 1; index <= 20; index += 1) {
    const erpStudentId = `STAGE-STU-${index.toString().padStart(4, '0')}`;
    await prisma.student.upsert({
      where: { schoolId_erpStudentId: { schoolId, erpStudentId } },
      update: {
        name: `Staging Student ${index}`,
        className: index <= 10 ? '9' : '10',
        section: index % 2 === 0 ? 'B' : 'A',
        rollNumber: index.toString(),
        status: StudentStatus.ACTIVE,
      },
      create: {
        schoolId,
        erpStudentId,
        name: `Staging Student ${index}`,
        className: index <= 10 ? '9' : '10',
        section: index % 2 === 0 ? 'B' : 'A',
        rollNumber: index.toString(),
        status: StudentStatus.ACTIVE,
      },
    });
  }

  console.log('Local staging seed ready');
  console.log(`schoolCode=LOCAL-STAGING`);
  console.log(`schoolId=${schoolId}`);
  console.log(`gateId=${gateId}`);
  console.log(`deviceId=${deviceId}`);
  console.log(`deviceAuthToken=${token}`);
  console.log('Use setup token from DEVICE_SETUP_TOKEN for app registration, or this token for direct API smoke tests.');
}

main()
  .then(async () => prisma.$disconnect())
  .catch(async (error) => {
    console.error(error);
    await prisma.$disconnect();
    process.exit(1);
  });
