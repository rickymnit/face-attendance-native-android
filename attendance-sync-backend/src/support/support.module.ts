import { Module } from '@nestjs/common';
import { SupportController } from './support.controller';
import { SupportRoleGuard } from './support-role.guard';
import { SupportService } from './support.service';

@Module({
  controllers: [SupportController],
  providers: [SupportService, SupportRoleGuard],
})
export class SupportModule {}
