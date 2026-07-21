ALTER TYPE "ImportJobItemStatus" ADD VALUE 'EMBEDDING_FAILED';
CREATE TYPE "EmbeddingJobStatus" AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

ALTER TABLE "School" ADD COLUMN "embeddingSyncVersion" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "FaceEmbeddingMetadata" ADD COLUMN "embeddingBase64" TEXT;
ALTER TABLE "ImportJob" ADD COLUMN "studentsCreated" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "ImportJob" ADD COLUMN "studentsUpdated" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "ImportJob" ADD COLUMN "embeddingsCreated" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "ImportJob" ADD COLUMN "failedEmbeddings" INTEGER NOT NULL DEFAULT 0;

CREATE TABLE "EmbeddingJob" (
    "id" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "erpStudentId" TEXT NOT NULL,
    "importJobItemId" TEXT,
    "status" "EmbeddingJobStatus" NOT NULL DEFAULT 'PENDING',
    "modelVersion" TEXT,
    "embeddingSize" INTEGER,
    "qualityScore" DOUBLE PRECISION,
    "errorCode" TEXT,
    "errorMessage" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "completedAt" TIMESTAMP(3),
    CONSTRAINT "EmbeddingJob_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "EmbeddingJob_importJobItemId_key" ON "EmbeddingJob"("importJobItemId");
CREATE INDEX "EmbeddingJob_schoolId_status_idx" ON "EmbeddingJob"("schoolId", "status");
CREATE INDEX "EmbeddingJob_schoolId_erpStudentId_idx" ON "EmbeddingJob"("schoolId", "erpStudentId");

ALTER TABLE "EmbeddingJob" ADD CONSTRAINT "EmbeddingJob_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "EmbeddingJob" ADD CONSTRAINT "EmbeddingJob_importJobItemId_fkey" FOREIGN KEY ("importJobItemId") REFERENCES "ImportJobItem"("id") ON DELETE SET NULL ON UPDATE CASCADE;
