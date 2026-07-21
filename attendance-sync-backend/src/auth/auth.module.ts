import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt-auth.guard';

@Module({
  imports: [
    JwtModule.registerAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        secret: config.get<string>('JWT_SECRET', 'dev-only-secret'),
        signOptions: { expiresIn: config.get<string>('JWT_EXPIRES_IN', '30d') },
      }),
    }),
  ],
  providers: [AuthService, JwtAuthGuard],
  exports: [JwtModule, AuthService, JwtAuthGuard],
})
export class AuthModule {}
