import { Injectable } from '@nestjs/common';
import { AttendanceDailySummary, SchoolAttendanceRule, Student } from '@prisma/client';
import { ErpAttendanceAdapter, ErpBatchPushResult, ErpPushResult } from './erp-attendance-adapter';

@Injectable()
export class MockErpAttendanceAdapter implements ErpAttendanceAdapter {
  async pushAttendanceSummary(summary: AttendanceDailySummary): Promise<ErpPushResult> {
    return { accepted: true, duplicate: false, erpReferenceId: `mock-erp-${summary.id}` };
  }

  async pushAttendanceBatch(summaries: AttendanceDailySummary[]): Promise<ErpBatchPushResult> {
    return {
      results: summaries.map((summary) => ({
        summaryId: summary.id,
        accepted: true,
        duplicate: false,
        erpReferenceId: `mock-erp-${summary.id}`,
      })),
    };
  }

  async fetchStudents(_schoolId: string): Promise<Student[]> {
    return [];
  }

  async verifyStudentExists(_schoolId: string, _erpStudentId: string): Promise<boolean> {
    return true;
  }

  async fetchSchoolAttendanceRules(_schoolId: string): Promise<SchoolAttendanceRule | null> {
    return null;
  }
}
