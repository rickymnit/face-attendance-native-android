-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "SchoolStatus" AS ENUM ('ACTIVE', 'INACTIVE');

-- CreateEnum
CREATE TYPE "DeviceStatus" AS ENUM ('ACTIVE', 'DISABLED', 'UNBOUND');

-- CreateEnum
CREATE TYPE "StudentStatus" AS ENUM ('ACTIVE', 'INACTIVE', 'DELETED');

-- CreateEnum
CREATE TYPE "EmbeddingStatus" AS ENUM ('ACTIVE', 'PENDING_APPROVAL', 'DELETED');

-- CreateEnum
CREATE TYPE "AttendanceEventType" AS ENUM ('PRESENT', 'LATE', 'HALF_DAY', 'OUT');

-- CreateEnum
CREATE TYPE "SyncStatus" AS ENUM ('PENDING', 'ACCEPTED', 'SYNCED_TO_ERP', 'FAILED', 'DUPLICATE');

-- CreateEnum
CREATE TYPE "EnrollmentStatus" AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "AuditAction" AS ENUM ('DEVICE_REGISTERED', 'DEVICE_HEARTBEAT', 'ATTENDANCE_SYNCED', 'FAILED_RECOGNITION_SYNCED', 'ENROLLMENT_REQUESTED', 'EMBEDDING_DELTA_FETCHED', 'ERP_SYNC_REQUESTED');

-- CreateTable
CREATE TABLE "School" (
    "id" TEXT NOT NULL,
    "code" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "status" "SchoolStatus" NOT NULL DEFAULT 'ACTIVE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "School_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Device" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "gateId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "status" "DeviceStatus" NOT NULL DEFAULT 'ACTIVE',
    "authTokenHash" TEXT,
    "configVersion" INTEGER NOT NULL DEFAULT 1,
    "embeddingSyncVersion" INTEGER NOT NULL DEFAULT 0,
    "deviceInfo" JSONB,
    "appVersion" TEXT,
    "lastHeartbeatAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Device_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Student" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "className" TEXT NOT NULL,
    "section" TEXT,
    "rollNumber" TEXT,
    "status" "StudentStatus" NOT NULL DEFAULT 'ACTIVE',
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Student_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FaceEmbeddingMetadata" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "modelVersion" TEXT NOT NULL,
    "embeddingSyncVersion" INTEGER NOT NULL,
    "embeddingSize" INTEGER NOT NULL,
    "qualityScore" DOUBLE PRECISION,
    "source" TEXT NOT NULL,
    "status" "EmbeddingStatus" NOT NULL DEFAULT 'ACTIVE',
    "checksum" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "FaceEmbeddingMetadata_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AttendanceEvent" (
    "id" TEXT NOT NULL,
    "eventId" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "gateId" TEXT NOT NULL,
    "eventType" "AttendanceEventType" NOT NULL,
    "attendanceDate" DATE NOT NULL,
    "timestampLocal" TIMESTAMP(3) NOT NULL,
    "timestampServer" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "matchScore" DOUBLE PRECISION NOT NULL,
    "livenessScore" DOUBLE PRECISION NOT NULL,
    "qualityScore" DOUBLE PRECISION NOT NULL,
    "modelVersion" TEXT,
    "syncStatus" "SyncStatus" NOT NULL DEFAULT 'ACCEPTED',
    "rawPayload" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AttendanceEvent_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AttendanceDailySummary" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "attendanceDate" DATE NOT NULL,
    "status" "AttendanceEventType" NOT NULL,
    "firstEventId" TEXT NOT NULL,
    "firstSeenAt" TIMESTAMP(3) NOT NULL,
    "lastSeenAt" TIMESTAMP(3) NOT NULL,
    "eventCount" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AttendanceDailySummary_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FailedRecognitionLog" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "gateId" TEXT,
    "reason" TEXT NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "qualityScore" DOUBLE PRECISION,
    "livenessScore" DOUBLE PRECISION,
    "modelVersion" TEXT,
    "syncStatus" "SyncStatus" NOT NULL DEFAULT 'ACCEPTED',
    "rawPayload" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "FailedRecognitionLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "EnrollmentRequest" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "deviceId" TEXT NOT NULL,
    "gateId" TEXT,
    "studentName" TEXT,
    "className" TEXT,
    "section" TEXT,
    "rollNumber" TEXT,
    "modelVersion" TEXT,
    "qualityScore" DOUBLE PRECISION,
    "status" "EnrollmentStatus" NOT NULL DEFAULT 'PENDING',
    "rawPayload" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "EnrollmentRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SchoolAttendanceRule" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "schoolStartTime" TEXT NOT NULL DEFAULT '08:00',
    "lateAfterTime" TEXT NOT NULL DEFAULT '08:15',
    "halfDayAfterTime" TEXT NOT NULL DEFAULT '10:30',
    "duplicateScanCooldownMinutes" INTEGER NOT NULL DEFAULT 10,
    "requireOutTime" BOOLEAN NOT NULL DEFAULT false,
    "recognitionMode" TEXT NOT NULL DEFAULT 'Strict',
    "configVersion" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SchoolAttendanceRule_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "AuditLog" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT,
    "deviceId" TEXT,
    "actorType" TEXT NOT NULL,
    "actorId" TEXT,
    "action" "AuditAction" NOT NULL,
    "entityType" TEXT NOT NULL,
    "entityId" TEXT,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "School_code_key" ON "School"("code");

-- CreateIndex
CREATE INDEX "Device_schoolId_gateId_idx" ON "Device"("schoolId", "gateId");

-- CreateIndex
CREATE INDEX "Device_status_idx" ON "Device"("status");

-- CreateIndex
CREATE INDEX "Student_schoolId_status_idx" ON "Student"("schoolId", "status");

-- CreateIndex
CREATE UNIQUE INDEX "Student_schoolId_erpStudentId_key" ON "Student"("schoolId", "erpStudentId");

-- CreateIndex
CREATE INDEX "FaceEmbeddingMetadata_schoolId_modelVersion_status_idx" ON "FaceEmbeddingMetadata"("schoolId", "modelVersion", "status");

-- CreateIndex
CREATE INDEX "FaceEmbeddingMetadata_schoolId_embeddingSyncVersion_idx" ON "FaceEmbeddingMetadata"("schoolId", "embeddingSyncVersion");

-- CreateIndex
CREATE INDEX "AttendanceEvent_schoolId_erpStudentId_attendanceDate_idx" ON "AttendanceEvent"("schoolId", "erpStudentId", "attendanceDate");

-- CreateIndex
CREATE INDEX "AttendanceEvent_deviceId_timestampServer_idx" ON "AttendanceEvent"("deviceId", "timestampServer");

-- CreateIndex
CREATE INDEX "AttendanceEvent_syncStatus_idx" ON "AttendanceEvent"("syncStatus");

-- CreateIndex
CREATE UNIQUE INDEX "AttendanceEvent_schoolId_eventId_key" ON "AttendanceEvent"("schoolId", "eventId");

-- CreateIndex
CREATE INDEX "AttendanceDailySummary_schoolId_attendanceDate_idx" ON "AttendanceDailySummary"("schoolId", "attendanceDate");

-- CreateIndex
CREATE UNIQUE INDEX "AttendanceDailySummary_schoolId_erpStudentId_attendanceDate_key" ON "AttendanceDailySummary"("schoolId", "erpStudentId", "attendanceDate");

-- CreateIndex
CREATE INDEX "FailedRecognitionLog_schoolId_timestamp_idx" ON "FailedRecognitionLog"("schoolId", "timestamp");

-- CreateIndex
CREATE INDEX "FailedRecognitionLog_deviceId_timestamp_idx" ON "FailedRecognitionLog"("deviceId", "timestamp");

-- CreateIndex
CREATE INDEX "EnrollmentRequest_schoolId_erpStudentId_status_idx" ON "EnrollmentRequest"("schoolId", "erpStudentId", "status");

-- CreateIndex
CREATE INDEX "EnrollmentRequest_deviceId_createdAt_idx" ON "EnrollmentRequest"("deviceId", "createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "SchoolAttendanceRule_schoolId_key" ON "SchoolAttendanceRule"("schoolId");

-- CreateIndex
CREATE INDEX "AuditLog_schoolId_createdAt_idx" ON "AuditLog"("schoolId", "createdAt");

-- CreateIndex
CREATE INDEX "AuditLog_deviceId_createdAt_idx" ON "AuditLog"("deviceId", "createdAt");

-- CreateIndex
CREATE INDEX "AuditLog_action_createdAt_idx" ON "AuditLog"("action", "createdAt");

-- AddForeignKey
ALTER TABLE "Device" ADD CONSTRAINT "Device_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Student" ADD CONSTRAINT "Student_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "FaceEmbeddingMetadata" ADD CONSTRAINT "FaceEmbeddingMetadata_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "FaceEmbeddingMetadata" ADD CONSTRAINT "FaceEmbeddingMetadata_schoolId_erpStudentId_fkey" FOREIGN KEY ("schoolId", "erpStudentId") REFERENCES "Student"("schoolId", "erpStudentId") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AttendanceEvent" ADD CONSTRAINT "AttendanceEvent_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AttendanceEvent" ADD CONSTRAINT "AttendanceEvent_schoolId_erpStudentId_fkey" FOREIGN KEY ("schoolId", "erpStudentId") REFERENCES "Student"("schoolId", "erpStudentId") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AttendanceEvent" ADD CONSTRAINT "AttendanceEvent_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AttendanceDailySummary" ADD CONSTRAINT "AttendanceDailySummary_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AttendanceDailySummary" ADD CONSTRAINT "AttendanceDailySummary_schoolId_erpStudentId_fkey" FOREIGN KEY ("schoolId", "erpStudentId") REFERENCES "Student"("schoolId", "erpStudentId") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "FailedRecognitionLog" ADD CONSTRAINT "FailedRecognitionLog_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "FailedRecognitionLog" ADD CONSTRAINT "FailedRecognitionLog_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "EnrollmentRequest" ADD CONSTRAINT "EnrollmentRequest_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "EnrollmentRequest" ADD CONSTRAINT "EnrollmentRequest_schoolId_erpStudentId_fkey" FOREIGN KEY ("schoolId", "erpStudentId") REFERENCES "Student"("schoolId", "erpStudentId") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "EnrollmentRequest" ADD CONSTRAINT "EnrollmentRequest_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SchoolAttendanceRule" ADD CONSTRAINT "SchoolAttendanceRule_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AuditLog" ADD CONSTRAINT "AuditLog_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "AuditLog" ADD CONSTRAINT "AuditLog_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE SET NULL ON UPDATE CASCADE;

