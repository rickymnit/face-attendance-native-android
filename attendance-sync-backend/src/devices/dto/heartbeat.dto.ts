import { IsBoolean, IsInt, IsNotEmpty, IsNumber, IsOptional, IsString, Max, Min } from 'class-validator';

export class DeviceHeartbeatDto {
  @IsString()
  @IsNotEmpty()
  deviceId!: string;

  @IsString()
  @IsNotEmpty()
  schoolId!: string;

  @IsString()
  @IsNotEmpty()
  gateId!: string;

  @IsString()
  @IsNotEmpty()
  timestamp!: string;

  @IsString()
  @IsOptional()
  appVersion?: string;

  @IsString()
  @IsOptional()
  modelVersion?: string;

  @IsInt()
  @Min(0)
  pendingAttendanceCount!: number;

  @IsInt()
  @Min(0)
  pendingFailedRecognitionCount!: number;

  @IsNumber()
  @Min(0)
  @Max(100)
  @IsOptional()
  batteryPercent?: number;

  @IsBoolean()
  @IsOptional()
  isCharging?: boolean;

  @IsString()
  @IsOptional()
  networkType?: string;
}
