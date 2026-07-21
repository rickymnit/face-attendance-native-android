import { Controller, Get, Param, UseGuards } from '@nestjs/common';
import { SupportRoleGuard } from './support-role.guard';
import { SupportService } from './support.service';

@Controller('support')
@UseGuards(SupportRoleGuard)
export class SupportController {
  constructor(private readonly supportService: SupportService) {}

  @Get('schools/health')
  schoolsHealth() {
    return this.supportService.schoolsHealth();
  }

  @Get('schools/:schoolId/devices')
  devices(@Param('schoolId') schoolId: string) {
    return this.supportService.devicesForSchool(schoolId);
  }

  @Get('schools/:schoolId/today-summary')
  todaySummary(@Param('schoolId') schoolId: string) {
    return this.supportService.todaySummary(schoolId);
  }

  @Get('schools/:schoolId/failed-recognitions')
  failedRecognitions(@Param('schoolId') schoolId: string) {
    return this.supportService.failedRecognitions(schoolId);
  }

  @Get('schools/:schoolId/sync-health')
  syncHealth(@Param('schoolId') schoolId: string) {
    return this.supportService.syncHealth(schoolId);
  }

  @Get('devices/offline')
  offlineDevices() {
    return this.supportService.offlineDevices();
  }

  @Get('devices/:deviceId/logs-summary')
  logsSummary(@Param('deviceId') deviceId: string) {
    return this.supportService.logsSummary(deviceId);
  }
}
