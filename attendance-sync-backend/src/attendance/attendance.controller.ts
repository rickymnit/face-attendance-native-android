import { Body, Controller, Post, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { AttendanceService } from './attendance.service';
import { SyncAttendanceEventsDto } from './dto/sync-attendance-events.dto';
import { SyncFailedRecognitionsDto } from './dto/sync-failed-recognitions.dto';

@Controller('attendance')
@UseGuards(JwtAuthGuard)
export class AttendanceController {
  constructor(private readonly attendanceService: AttendanceService) {}

  @Post('events/sync')
  syncEvents(@Body() dto: SyncAttendanceEventsDto, @Req() request: AuthenticatedRequest) {
    return this.attendanceService.syncEvents(dto, request.deviceAuth!);
  }

  @Post('failed/sync')
  syncFailed(@Body() dto: SyncFailedRecognitionsDto, @Req() request: AuthenticatedRequest) {
    return this.attendanceService.syncFailedRecognitions(dto, request.deviceAuth!);
  }
}
