import { Body, Controller, Get, Param, Post, Req, UseGuards } from '@nestjs/common';
import { AuthenticatedRequest } from '../common/authenticated-request';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DeviceHeartbeatDto } from './dto/heartbeat.dto';
import { RegisterDeviceDto } from './dto/register-device.dto';
import { DevicesService } from './devices.service';

@Controller('devices')
export class DevicesController {
  constructor(private readonly devicesService: DevicesService) {}

  @Post('register')
  register(@Body() dto: RegisterDeviceDto) {
    return this.devicesService.register(dto);
  }

  @Post('heartbeat')
  @UseGuards(JwtAuthGuard)
  heartbeat(@Body() dto: DeviceHeartbeatDto, @Req() request: AuthenticatedRequest) {
    return this.devicesService.heartbeat(dto, request.deviceAuth!);
  }

  @Get(':deviceId/config')
  @UseGuards(JwtAuthGuard)
  config(@Param('deviceId') deviceId: string, @Req() request: AuthenticatedRequest) {
    return this.devicesService.getConfig(deviceId, request.deviceAuth!);
  }
}
