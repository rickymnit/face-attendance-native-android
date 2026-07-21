import { Request } from 'express';

export interface DeviceAuthContext {
  deviceId: string;
  schoolId: string;
}

export interface AuthenticatedRequest extends Request {
  deviceAuth?: DeviceAuthContext;
}
