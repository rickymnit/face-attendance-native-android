import { Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';

export interface DeviceTokenPayload {
  sub: string;
  deviceId: string;
  schoolId: string;
}

@Injectable()
export class AuthService {
  constructor(private readonly jwtService: JwtService) {}

  async issueDeviceToken(deviceId: string, schoolId: string): Promise<string> {
    const payload: DeviceTokenPayload = { sub: deviceId, deviceId, schoolId };
    return this.jwtService.signAsync(payload);
  }
}
