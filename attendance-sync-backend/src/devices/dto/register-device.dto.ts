import { IsNotEmpty, IsObject, IsOptional, IsString } from 'class-validator';

export class RegisterDeviceDto {
  @IsString()
  @IsNotEmpty()
  schoolCode!: string;

  @IsString()
  @IsNotEmpty()
  gateId!: string;

  @IsString()
  @IsNotEmpty()
  deviceName!: string;

  @IsString()
  @IsNotEmpty()
  setupToken!: string;

  @IsObject()
  @IsOptional()
  deviceInfo?: Record<string, unknown>;
}
