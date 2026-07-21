import { Controller, Get, Param, Query, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { EmbeddingsService } from './embeddings.service';

@Controller('schools/:schoolId/embeddings')
@UseGuards(JwtAuthGuard)
export class EmbeddingsController {
  constructor(private readonly embeddingsService: EmbeddingsService) {}

  @Get('delta')
  delta(
    @Param('schoolId') schoolId: string,
    @Query('sinceVersion') sinceVersion: string | undefined,
    @Query('modelVersion') modelVersion: string | undefined,
    @Query('limit') limit: string | undefined,
    @Req() request: AuthenticatedRequest,
  ) {
    return this.embeddingsService.delta(schoolId, sinceVersion, request.deviceAuth!, modelVersion, limit);
  }
}
