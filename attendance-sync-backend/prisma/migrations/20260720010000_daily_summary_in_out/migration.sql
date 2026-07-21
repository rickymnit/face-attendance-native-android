ALTER TABLE "AttendanceDailySummary" ADD COLUMN "inTime" TIMESTAMP(3);
ALTER TABLE "AttendanceDailySummary" ADD COLUMN "outTime" TIMESTAMP(3);
UPDATE "AttendanceDailySummary" SET "inTime" = "firstSeenAt" WHERE "status" <> 'OUT';
UPDATE "AttendanceDailySummary" SET "outTime" = "lastSeenAt" WHERE "status" = 'OUT';
