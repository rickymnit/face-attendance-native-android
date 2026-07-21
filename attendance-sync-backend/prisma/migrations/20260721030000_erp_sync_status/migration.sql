ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'ERP_SYNC_SUCCEEDED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'ERP_SYNC_FAILED';
ALTER TYPE "AuditAction" ADD VALUE IF NOT EXISTS 'ERP_SYNC_RETRY_REQUESTED';

CREATE TYPE "ErpSyncState" AS ENUM ('PENDING', 'SYNCED', 'FAILED');

CREATE TABLE "ErpSyncStatus" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "attendanceDate" DATE NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "status" "ErpSyncState" NOT NULL DEFAULT 'PENDING',
    "retryCount" INTEGER NOT NULL DEFAULT 0,
    "lastError" TEXT,
    "lastTriedAt" TIMESTAMP(3),
    "syncedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "ErpSyncStatus_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "ErpSyncStatus_schoolId_erpStudentId_attendanceDate_key" ON "ErpSyncStatus"("schoolId", "erpStudentId", "attendanceDate");
CREATE INDEX "ErpSyncStatus_schoolId_attendanceDate_status_idx" ON "ErpSyncStatus"("schoolId", "attendanceDate", "status");
CREATE INDEX "ErpSyncStatus_status_lastTriedAt_idx" ON "ErpSyncStatus"("status", "lastTriedAt");
ALTER TABLE "ErpSyncStatus" ADD CONSTRAINT "ErpSyncStatus_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
