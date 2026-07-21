import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AttendanceDailySummary, SchoolAttendanceRule, Student } from '@prisma/client';
import { ErpAttendanceAdapter, ErpBatchPushResult, ErpPushResult } from './erp-attendance-adapter';

@Injectable()
export class HttpErpAttendanceAdapter implements ErpAttendanceAdapter {
  constructor(private readonly configService: ConfigService) {}

  async pushAttendanceSummary(summary: AttendanceDailySummary): Promise<ErpPushResult> {
    const baseUrl = this.configService.get<string>('ERP_BASE_URL');
    const apiKey = this.configService.get<string>('ERP_API_KEY');
    if (!baseUrl || !apiKey) {
      throw new ServiceUnavailableException('HTTP ERP adapter is not configured');
    }
    // Placeholder until the Schoollog ERP endpoint is finalized. Keep the adapter boundary stable.
    return { accepted: false, message: 'HTTP ERP adapter endpoint not implemented' };
  }

  async pushAttendanceBatch(summaries: AttendanceDailySummary[]): Promise<ErpBatchPushResult> {
    const results = [];
    for (const summary of summaries) {
      const result = await this.pushAttendanceSummary(summary);
      results.push({ summaryId: summary.id, ...result });
    }
    return { results };
  }

  async fetchStudents(_schoolId: string): Promise<Student[]> {
    throw new ServiceUnavailableException('HTTP ERP student fetch is not implemented');
  }

  async verifyStudentExists(_schoolId: string, _erpStudentId: string): Promise<boolean> {
    throw new ServiceUnavailableException('HTTP ERP student verification is not implemented');
  }

  async fetchSchoolAttendanceRules(_schoolId: string): Promise<SchoolAttendanceRule | null> {
    throw new ServiceUnavailableException('HTTP ERP attendance rules fetch is not implemented');
  }
}
