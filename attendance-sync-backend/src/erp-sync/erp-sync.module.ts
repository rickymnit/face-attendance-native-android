import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AuditModule } from '../audit/audit.module';
import { AuthModule } from '../auth/auth.module';
import { ERP_ATTENDANCE_ADAPTER } from './erp-attendance-adapter';
import { ErpSyncController } from './erp-sync.controller';
import { ErpSyncService } from './erp-sync.service';
import { HttpErpAttendanceAdapter } from './http-erp-attendance.adapter';
import { MockErpAttendanceAdapter } from './mock-erp-attendance.adapter';

@Module({
  imports: [AuthModule, AuditModule],
  controllers: [ErpSyncController],
  providers: [
    ErpSyncService,
    MockErpAttendanceAdapter,
    HttpErpAttendanceAdapter,
    {
      provide: ERP_ATTENDANCE_ADAPTER,
      inject: [ConfigService, MockErpAttendanceAdapter, HttpErpAttendanceAdapter],
      useFactory: (config: ConfigService, mock: MockErpAttendanceAdapter, http: HttpErpAttendanceAdapter) =>
        config.get<string>('ERP_ADAPTER', 'mock') === 'http' ? http : mock,
    },
  ],
  exports: [ErpSyncService],
})
export class ErpSyncModule {}
