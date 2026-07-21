import { Body, Controller, Get, Param, Post, Query, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { RetryErpSyncDto } from './dto/retry-erp-sync.dto';
import { ErpSyncService } from './erp-sync.service';

@Controller()
@UseGuards(JwtAuthGuard)
export class ErpSyncController {
  constructor(private readonly erpSyncService: ErpSyncService) {}

  @Post('erp-sync/retry')
  retry(@Body() dto: RetryErpSyncDto, @Req() request: AuthenticatedRequest) {
    return this.erpSyncService.retrySchool(dto.schoolId, request.deviceAuth!);
  }

  @Get('schools/:schoolId/erp-sync/status')
  status(
    @Param('schoolId') schoolId: string,
    @Query('date') date: string | undefined,
    @Req() request: AuthenticatedRequest,
  ) {
    return this.erpSyncService.statusForSchool(schoolId, date, request.deviceAuth!);
  }
}
