import { Type } from 'class-transformer';
import { IsArray, IsNotEmpty, IsNumber, IsOptional, IsString, ValidateNested } from 'class-validator';

export class FailedRecognitionSyncItemDto {
  @IsString()
  @IsNotEmpty()
  failedRecognitionId!: string;

  @IsString()
  @IsOptional()
  schoolId?: string;

  @IsString()
  @IsOptional()
  deviceId?: string;

  @IsString()
  @IsOptional()
  gateId?: string;

  @IsString()
  @IsNotEmpty()
  reason!: string;

  @IsNotEmpty()
  timestamp!: string | number;

  @IsNumber()
  @IsOptional()
  qualityScore?: number;

  @IsNumber()
  @IsOptional()
  livenessScore?: number;

  @IsString()
  @IsOptional()
  modelVersion?: string;
}

export class SyncFailedRecognitionsDto {
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
  @Type(() => FailedRecognitionSyncItemDto)
  failedRecognitions!: FailedRecognitionSyncItemDto[];
}
