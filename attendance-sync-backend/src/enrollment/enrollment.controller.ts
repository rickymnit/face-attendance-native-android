import { Body, Controller, Get, Param, Post, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { CreateEnrollmentRequestDto } from './dto/create-enrollment-request.dto';
import { EnrollmentService } from './enrollment.service';

@Controller('enrollment/requests')
@UseGuards(JwtAuthGuard)
export class EnrollmentController {
  constructor(private readonly enrollmentService: EnrollmentService) {}

  @Post()
  create(@Body() dto: CreateEnrollmentRequestDto, @Req() request: AuthenticatedRequest) {
    return this.enrollmentService.create(dto, request.deviceAuth!);
  }

  @Get(':id')
  get(@Param('id') id: string, @Req() request: AuthenticatedRequest) {
    return this.enrollmentService.get(id, request.deviceAuth!);
  }
}
