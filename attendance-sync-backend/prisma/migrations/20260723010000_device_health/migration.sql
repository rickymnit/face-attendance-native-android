CREATE TABLE "DeviceHealth" (
    "deviceId" TEXT NOT NULL,
    "schoolId" TEXT NOT NULL,
    "gateId" TEXT NOT NULL,
    "appVersion" TEXT,
    "modelVersion" TEXT,
    "embeddingCount" INTEGER NOT NULL DEFAULT 0,
    "pendingAttendanceCount" INTEGER NOT NULL DEFAULT 0,
    "pendingFailedRecognitionCount" INTEGER NOT NULL DEFAULT 0,
    "lastAttendanceSyncAt" TIMESTAMP(3),
    "lastEmbeddingSyncAt" TIMESTAMP(3),
    "batteryPercent" INTEGER,
    "isCharging" BOOLEAN,
    "networkStatus" TEXT,
    "cameraStatus" TEXT,
    "averageDecisionTime" DOUBLE PRECISION,
    "lastError" TEXT,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "DeviceHealth_pkey" PRIMARY KEY ("deviceId")
);

CREATE INDEX "DeviceHealth_schoolId_updatedAt_idx" ON "DeviceHealth"("schoolId", "updatedAt");
CREATE INDEX "DeviceHealth_networkStatus_idx" ON "DeviceHealth"("networkStatus");

ALTER TABLE "DeviceHealth" ADD CONSTRAINT "DeviceHealth_deviceId_fkey" FOREIGN KEY ("deviceId") REFERENCES "Device"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "DeviceHealth" ADD CONSTRAINT "DeviceHealth_schoolId_fkey" FOREIGN KEY ("schoolId") REFERENCES "School"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
