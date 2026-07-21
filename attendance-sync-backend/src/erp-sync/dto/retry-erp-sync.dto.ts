import { IsNotEmpty, IsString } from 'class-validator';

export class RetryErpSyncDto {
  @IsString()
  @IsNotEmpty()
  schoolId!: string;
}
