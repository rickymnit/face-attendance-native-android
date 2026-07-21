import { BadRequestException, Controller, Get, Param, Post, Req, UploadedFiles, UseGuards, UseInterceptors } from '@nestjs/common';
import { FileFieldsInterceptor } from '@nestjs/platform-express';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { StudentImportService } from './student-import.service';

@Controller('schools/:schoolId')
@UseGuards(JwtAuthGuard)
export class StudentImportController {
  constructor(private readonly studentImportService: StudentImportService) {}

  @Post('import/students')
  @UseInterceptors(FileFieldsInterceptor([
    { name: 'csv', maxCount: 1 },
    { name: 'zip', maxCount: 1 },
  ]))
  importStudents(
    @Param('schoolId') schoolId: string,
    @UploadedFiles() files: { csv?: any[]; zip?: any[] },
    @Req() request: AuthenticatedRequest,
  ) {
    const csvFile = files?.csv?.[0];
    const zipFile = files?.zip?.[0];
    if (!csvFile || !zipFile) {
      throw new BadRequestException('Both csv and zip files are required');
    }
    return this.studentImportService.importStudents(schoolId, csvFile, zipFile, request.deviceAuth!);
  }

  @Get('imports/:importId/status')
  status(
    @Param('schoolId') schoolId: string,
    @Param('importId') importId: string,
    @Req() request: AuthenticatedRequest,
  ) {
    return this.studentImportService.getStatus(schoolId, importId, request.deviceAuth!);
  }

  @Get('imports/:importId/errors')
  errors(
    @Param('schoolId') schoolId: string,
    @Param('importId') importId: string,
    @Req() request: AuthenticatedRequest,
  ) {
    return this.studentImportService.getErrors(schoolId, importId, request.deviceAuth!);
  }
}
