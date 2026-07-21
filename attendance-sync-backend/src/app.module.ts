import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AttendanceModule } from './attendance/attendance.module';
import { AuditModule } from './audit/audit.module';
import { AuthModule } from './auth/auth.module';
import { DevicesModule } from './devices/devices.module';
import { EmbeddingsModule } from './embeddings/embeddings.module';
import { EnrollmentModule } from './enrollment/enrollment.module';
import { ErpSyncModule } from './erp-sync/erp-sync.module';
import { PrismaModule } from './prisma/prisma.module';
import { SchoolsModule } from './schools/schools.module';
import { DeviceRateLimitMiddleware } from './common/device-rate-limit.middleware';
import { validateEnvironment } from './common/environment.validation';
import { StudentImportModule } from './student-import/student-import.module';
import { SupportModule } from './support/support.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, validate: validateEnvironment }),
    PrismaModule,
    AuthModule,
    AuditModule,
    ErpSyncModule,
    SchoolsModule,
    DevicesModule,
    AttendanceModule,
    EnrollmentModule,
    EmbeddingsModule,
    StudentImportModule,
    SupportModule,
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(DeviceRateLimitMiddleware).forRoutes('*');
  }
}

