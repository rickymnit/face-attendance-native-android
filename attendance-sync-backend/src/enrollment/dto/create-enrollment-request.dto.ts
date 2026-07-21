import { IsNotEmpty, IsNumber, IsObject, IsOptional, IsString } from 'class-validator';

export class CreateEnrollmentRequestDto {
  @IsString()
  @IsNotEmpty()
  schoolId!: string;

  @IsString()
  @IsNotEmpty()
  deviceId!: string;

  @IsString()
  @IsOptional()
  gateId?: string;

  @IsString()
  @IsNotEmpty()
  erpStudentId!: string;

  @IsString()
  @IsOptional()
  studentName?: string;

  @IsString()
  @IsOptional()
  className?: string;

  @IsString()
  @IsOptional()
  section?: string;

  @IsString()
  @IsOptional()
  rollNumber?: string;

  @IsString()
  @IsOptional()
  modelVersion?: string;

  @IsNumber()
  @IsOptional()
  qualityScore?: number;

  @IsObject()
  @IsOptional()
  metadata?: Record<string, unknown>;
}
