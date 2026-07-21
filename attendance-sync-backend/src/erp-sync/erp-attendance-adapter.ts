import { AttendanceDailySummary, SchoolAttendanceRule, Student } from '@prisma/client';

export interface ErpAttendanceAdapter {
  pushAttendanceSummary(summary: AttendanceDailySummary): Promise<ErpPushResult>;
  pushAttendanceBatch(summaries: AttendanceDailySummary[]): Promise<ErpBatchPushResult>;
  fetchStudents(schoolId: string): Promise<Student[]>;
  verifyStudentExists(schoolId: string, erpStudentId: string): Promise<boolean>;
  fetchSchoolAttendanceRules(schoolId: string): Promise<SchoolAttendanceRule | null>;
}

export interface ErpPushResult {
  accepted: boolean;
  duplicate?: boolean;
  erpReferenceId?: string;
  message?: string;
}

export interface ErpBatchPushResult {
  results: Array<{
    summaryId: string;
    accepted: boolean;
    duplicate?: boolean;
    erpReferenceId?: string;
    message?: string;
  }>;
}

export const ERP_ATTENDANCE_ADAPTER = Symbol('ERP_ATTENDANCE_ADAPTER');
