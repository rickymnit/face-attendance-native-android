CREATE TYPE "ImportJobStatus" AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED');
CREATE TYPE "ImportJobItemStatus" AS ENUM ('PENDING', 'VALIDATED', 'FAILED', 'EMBEDDING_PENDING', 'EMBEDDING_DONE');
ALTER TYPE "AuditAction" ADD VALUE 'STUDENT_IMPORT_CREATED';

CREATE TABLE "ImportJob" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "status" "ImportJobStatus" NOT NULL DEFAULT 'PENDING',
    "totalRows" INTEGER NOT NULL DEFAULT 0,
    "validRows" INTEGER NOT NULL DEFAULT 0,
    "failedRows" INTEGER NOT NULL DEFAULT 0,
    "sourceFileName" TEXT,
    "zipFileName" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "completedAt" TIMESTAMP(3),
    CONSTRAINT "ImportJob_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "ImportJobItem" (
    "id" TEXT NOT NULL,
    "importJobId" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "rowNumber" INTEGER NOT NULL,
    "erpStudentId" TEXT,
    "name" TEXT,
    "className" TEXT,
    "section" TEXT,
    "rollNumber" TEXT,
    "photoFileName" TEXT,
    "status" "ImportJobItemStatus" NOT NULL DEFAULT 'PENDING',
    "errorCode" TEXT,
    "errorMessage" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "ImportJobItem_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "ImportJob_schoolId_createdAt_idx" ON "ImportJob"("schoolId", "createdAt");
CREATE INDEX "ImportJob_status_idx" ON "ImportJob"("status");
CREATE INDEX "ImportJobItem_importJobId_status_idx" ON "ImportJobItem"("importJobId", "status");
CREATE INDEX "ImportJobItem_schoolId_erpStudentId_idx" ON "ImportJobItem"("schoolId", "erpStudentId");

ALTER TABLE "ImportJob" ADD CONSTRAINT "ImportJob_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "ImportJobItem" ADD CONSTRAINT "ImportJobItem_importJobId_fkey" FOREIGN KEY ("importJobId") REFERENCES "ImportJob"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "ImportJobItem" ADD CONSTRAINT "ImportJobItem_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
