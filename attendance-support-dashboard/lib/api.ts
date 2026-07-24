export type SupportRole = 'SCHOOL_ADMIN' | 'SCHOOLLOG_SUPPORT' | 'SUPER_ADMIN';

export interface DashboardSession {
  baseUrl: string;
  role: SupportRole;
}

export type DeviceHealthStatus = 'HEALTHY' | 'WARNING' | 'CRITICAL';

export interface SchoolHealth {
  schoolId: string;
  schoolName: string;
  totalDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  pendingErpSync: number;
  failedRecognitionCount: number;
  embeddingSyncVersion: number;
  healthyDevices: number;
  warningDevices: number;
  criticalDevices: number;
}

export interface DeviceInfo {
  deviceId: string;
  gateId: string;
  name: string;
  status: string;
  online: boolean;
  healthStatus: DeviceHealthStatus;
  healthReason: string;
  lastHeartbeat: string | null;
  appVersion: string | null;
  modelVersion: string | null;
  embeddingCount: number | null;
  pendingAttendanceSync: number | null;
  pendingFailedRecognitionSync: number | null;
  lastAttendanceSyncAt: string | null;
  lastEmbeddingSyncAt: string | null;
  batteryPercent: number | null;
  isCharging: boolean | null;
  networkStatus: string | null;
  cameraStatus: string | null;
  averageDecisionTime: number | null;
  lastError: string | null;
  embeddingSyncVersion: number;
}

export interface TodaySummary {
  schoolId: string;
  attendanceDate: string;
  totalMarked: number;
  present: number;
  late: number;
  halfDay: number;
  out: number;
  recent: Array<{
    erpStudentId: string;
    name: string;
    className: string | null;
    status: string;
    inTime: string | null;
    outTime: string | null;
  }>;
}

export interface FailedRecognitions {
  schoolId: string;
  total: number;
  livenessFailureCount: number;
  logs: Array<{
    id: string;
    deviceId: string;
    gateId: string | null;
    reason: string;
    timestamp: string;
    qualityScore: number | null;
    livenessScore: number | null;
    modelVersion: string | null;
  }>;
}

export interface SyncHealth {
  schoolId: string;
  pendingErpSync: number;
  syncedErpSync: number;
  failedErpSync: number;
  failedRecognitionCount: number;
  lastAttendanceSync: string | null;
}

export interface OfflineDevice {
  schoolId: string;
  schoolName: string;
  deviceId: string;
  gateId: string;
  name: string;
  lastHeartbeat: string | null;
  appVersion: string | null;
  healthStatus: DeviceHealthStatus;
  healthReason: string;
}

export interface DeviceLogsSummary {
  deviceId: string;
  totalLogs: number;
  counts: Record<string, number>;
  lastLogAt: string | null;
  recent: Array<{
    action: string;
    entityType: string;
    entityId: string | null;
    createdAt: string;
  }>;
}

export class SupportApiClient {
  constructor(private readonly session: DashboardSession) {}

  schoolsHealth(): Promise<SchoolHealth[]> {
    return this.get('/api/support/schools/health');
  }

  devices(schoolId: string): Promise<DeviceInfo[]> {
    return this.get(`/api/support/schools/${encodeURIComponent(schoolId)}/devices`);
  }

  todaySummary(schoolId: string): Promise<TodaySummary> {
    return this.get(`/api/support/schools/${encodeURIComponent(schoolId)}/today-summary`);
  }

  failedRecognitions(schoolId: string): Promise<FailedRecognitions> {
    return this.get(`/api/support/schools/${encodeURIComponent(schoolId)}/failed-recognitions`);
  }

  syncHealth(schoolId: string): Promise<SyncHealth> {
    return this.get(`/api/support/schools/${encodeURIComponent(schoolId)}/sync-health`);
  }

  offlineDevices(): Promise<OfflineDevice[]> {
    return this.get('/api/support/devices/offline');
  }

  deviceLogsSummary(deviceId: string): Promise<DeviceLogsSummary> {
    return this.get(`/api/support/devices/${encodeURIComponent(deviceId)}/logs-summary`);
  }

  private async get<T>(path: string): Promise<T> {
    const response = await fetch(`/api/backend${path}`, {
      headers: {
        'x-schoollog-backend-url': this.session.baseUrl,
        'x-schoollog-support-role': this.session.role
      },
      cache: 'no-store'
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `HTTP ${response.status}`);
    }
    return response.json() as Promise<T>;
  }
}
