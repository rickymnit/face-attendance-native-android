import { Type } from 'class-transformer';
import { IsArray, IsIn, IsNotEmpty, IsNumber, IsOptional, IsString, ValidateNested } from 'class-validator';

export class AttendanceEventSyncItemDto {
  @IsString()
  @IsNotEmpty()
  eventId!: string;

  @IsString()
  @IsNotEmpty()
  schoolId!: string;

  @IsString()
  @IsNotEmpty()
  studentId!: string;

  @IsString()
  @IsOptional()
  erpStudentId?: string;

  @IsString()
  @IsNotEmpty()
  deviceId!: string;

  @IsString()
  @IsNotEmpty()
  gateId!: string;

  @IsString()
  @IsIn(['PRESENT', 'LATE', 'HALF_DAY', 'OUT'])
  eventType!: 'PRESENT' | 'LATE' | 'HALF_DAY' | 'OUT';

  @IsString()
  @IsOptional()
  attendanceDate?: string;

  @IsNotEmpty()
  timestamp!: string | number;

  @IsNumber()
  matchScore!: number;

  @IsNumber()
  livenessScore!: number;

  @IsNumber()
  qualityScore!: number;

  @IsString()
  @IsOptional()
  modelVersion?: string;

  @IsString()
  @IsOptional()
  recognitionMode?: string;
}

export class SyncAttendanceEventsDto {
  @IsString()
  @IsNotEmpty()
  deviceId!: string;

  @IsString()
  @IsNotEmpty()
  schoolId!: string;

  @IsString()
  @IsNotEmpty()
  gateId!: string;

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => AttendanceEventSyncItemDto)
  events!: AttendanceEventSyncItemDto[];
}
