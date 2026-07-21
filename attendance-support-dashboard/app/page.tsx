"use client";

import { useEffect, useMemo, useState } from 'react';
import {
  DeviceInfo,
  DeviceLogsSummary,
  FailedRecognitions,
  OfflineDevice,
  SchoolHealth,
  SupportApiClient,
  SupportRole,
  SyncHealth,
  TodaySummary
} from '../lib/api';

type Screen = 'schools' | 'devices' | 'today' | 'failed' | 'sync' | 'device-detail';

const roles: SupportRole[] = ['SCHOOLLOG_SUPPORT', 'SCHOOL_ADMIN', 'SUPER_ADMIN'];

export default function DashboardPage() {
  const [baseUrl, setBaseUrl] = useState('http://localhost:3000');
  const [role, setRole] = useState<SupportRole>('SCHOOLLOG_SUPPORT');
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [screen, setScreen] = useState<Screen>('schools');
  const [schools, setSchools] = useState<SchoolHealth[]>([]);
  const [selectedSchoolId, setSelectedSchoolId] = useState('');
  const [selectedDeviceId, setSelectedDeviceId] = useState('');
  const [devices, setDevices] = useState<DeviceInfo[]>([]);
  const [offlineDevices, setOfflineDevices] = useState<OfflineDevice[]>([]);
  const [today, setToday] = useState<TodaySummary | null>(null);
  const [failed, setFailed] = useState<FailedRecognitions | null>(null);
  const [syncHealth, setSyncHealth] = useState<SyncHealth | null>(null);
  const [deviceLogs, setDeviceLogs] = useState<DeviceLogsSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const api = useMemo(() => new SupportApiClient({ baseUrl, role }), [baseUrl, role]);
  const selectedSchool = schools.find((school) => school.schoolId === selectedSchoolId) || null;

  useEffect(() => {
    const stored = window.localStorage.getItem('schoollog-support-session');
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored) as { baseUrl?: string; role?: SupportRole };
      if (parsed.baseUrl) setBaseUrl(parsed.baseUrl);
      if (parsed.role && roles.includes(parsed.role)) setRole(parsed.role);
      setIsLoggedIn(true);
    } catch {
      window.localStorage.removeItem('schoollog-support-session');
    }
  }, []);

  useEffect(() => {
    if (isLoggedIn) void loadSchools();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  async function run<T>(loader: () => Promise<T>): Promise<T | null> {
    setLoading(true);
    setError(null);
    try {
      return await loader();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown API error');
      return null;
    } finally {
      setLoading(false);
    }
  }

  async function loadSchools() {
    const result = await run(() => api.schoolsHealth());
    if (!result) return;
    setSchools(result);
    if (!selectedSchoolId && result.length > 0) setSelectedSchoolId(result[0].schoolId);
  }

  async function loadDevices(schoolId = selectedSchoolId) {
    if (!schoolId) return;
    setScreen('devices');
    const [deviceResult, offlineResult] = await Promise.all([
      run(() => api.devices(schoolId)),
      api.offlineDevices().catch(() => [] as OfflineDevice[])
    ]);
    if (deviceResult) setDevices(deviceResult);
    setOfflineDevices(offlineResult);
  }

  async function loadToday(schoolId = selectedSchoolId) {
    if (!schoolId) return;
    setScreen('today');
    const result = await run(() => api.todaySummary(schoolId));
    if (result) setToday(result);
  }

  async function loadFailed(schoolId = selectedSchoolId) {
    if (!schoolId) return;
    setScreen('failed');
    const result = await run(() => api.failedRecognitions(schoolId));
    if (result) setFailed(result);
  }

  async function loadSync(schoolId = selectedSchoolId) {
    if (!schoolId) return;
    setScreen('sync');
    const result = await run(() => api.syncHealth(schoolId));
    if (result) setSyncHealth(result);
  }

  async function loadDeviceDetail(deviceId: string) {
    setSelectedDeviceId(deviceId);
    setScreen('device-detail');
    const result = await run(() => api.deviceLogsSummary(deviceId));
    if (result) setDeviceLogs(result);
  }

  function login() {
    window.localStorage.setItem('schoollog-support-session', JSON.stringify({ baseUrl, role }));
    setIsLoggedIn(true);
  }

  function logout() {
    window.localStorage.removeItem('schoollog-support-session');
    setIsLoggedIn(false);
  }

  if (!isLoggedIn) {
    return (
      <main className="page">
        <section className="shell card">
          <h1>Schoollog Support Dashboard</h1>
          <p className="muted">Login placeholder for support/admin monitoring. Backend role auth is currently provided by a support-role header.</p>
          <div className="form">
            <label>
              Backend API URL
              <input value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
            </label>
            <label>
              Role
              <select value={role} onChange={(event) => setRole(event.target.value as SupportRole)}>
                {roles.map((item) => <option key={item} value={item}>{item}</option>)}
              </select>
            </label>
            <button className="primary" onClick={login}>Continue</button>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="page">
      <section className="shell">
        <header className="header">
          <div>
            <h1>Schoollog Support Dashboard</h1>
            <p className="muted">{baseUrl} · {role}</p>
          </div>
          <button onClick={logout}>Logout</button>
        </header>

        <div className="toolbar card">
          <label>
            School
            <select value={selectedSchoolId} onChange={(event) => setSelectedSchoolId(event.target.value)}>
              {schools.map((school) => <option key={school.schoolId} value={school.schoolId}>{school.schoolName} ({school.schoolId})</option>)}
            </select>
          </label>
          <button onClick={loadSchools}>Refresh Schools</button>
        </div>

        <nav className="tabs">
          <button className={screen === 'schools' ? 'active' : ''} onClick={() => { setScreen('schools'); void loadSchools(); }}>Schools Health</button>
          <button className={screen === 'devices' ? 'active' : ''} onClick={() => void loadDevices()}>Devices</button>
          <button className={screen === 'today' ? 'active' : ''} onClick={() => void loadToday()}>Today Summary</button>
          <button className={screen === 'failed' ? 'active' : ''} onClick={() => void loadFailed()}>Failed Recognitions</button>
          <button className={screen === 'sync' ? 'active' : ''} onClick={() => void loadSync()}>ERP Sync</button>
          <button className={screen === 'device-detail' ? 'active' : ''} disabled={!selectedDeviceId} onClick={() => selectedDeviceId && void loadDeviceDetail(selectedDeviceId)}>Device Detail</button>
        </nav>

        {loading && <p className="muted">Loading...</p>}
        {error && <p className="error">{error}</p>}

        {screen === 'schools' && <SchoolsHealth schools={schools} onSelect={(schoolId) => { setSelectedSchoolId(schoolId); void loadDevices(schoolId); }} />}
        {screen === 'devices' && <DevicesList devices={devices} offlineDevices={offlineDevices} onOpenDevice={loadDeviceDetail} />}
        {screen === 'today' && <TodaySummaryPanel summary={today} school={selectedSchool} />}
        {screen === 'failed' && <FailedRecognitionsPanel failed={failed} />}
        {screen === 'sync' && <SyncHealthPanel syncHealth={syncHealth} />}
        {screen === 'device-detail' && <DeviceDetailPanel deviceId={selectedDeviceId} logs={deviceLogs} />}
      </section>
    </main>
  );
}

function SchoolsHealth({ schools, onSelect }: { schools: SchoolHealth[]; onSelect: (schoolId: string) => void }) {
  return (
    <section className="grid">
      {schools.map((school) => (
        <article className="card" key={school.schoolId}>
          <h2>{school.schoolName}</h2>
          <p className="muted">{school.schoolId}</p>
          <p><b>{school.onlineDevices}</b> online · <b>{school.offlineDevices}</b> offline</p>
          <p>Pending ERP sync: <b>{school.pendingErpSync}</b></p>
          <p>Failed recognitions today: <b>{school.failedRecognitionCount}</b></p>
          <p>Embedding sync version: <b>{school.embeddingSyncVersion}</b></p>
          <button onClick={() => onSelect(school.schoolId)}>Open Devices</button>
        </article>
      ))}
    </section>
  );
}

function DevicesList({ devices, offlineDevices, onOpenDevice }: { devices: DeviceInfo[]; offlineDevices: OfflineDevice[]; onOpenDevice: (deviceId: string) => void }) {
  return (
    <section className="card">
      <h2>Devices</h2>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Status</th><th>Device</th><th>Gate</th><th>Heartbeat</th><th>App</th><th>Model</th><th>Pending</th><th></th></tr></thead>
          <tbody>
            {devices.map((device) => (
              <tr key={device.deviceId}>
                <td className={device.online ? 'status-pass' : 'status-fail'}>{device.online ? 'ONLINE' : 'OFFLINE'}</td>
                <td>{device.name}<br /><span className="muted">{device.deviceId}</span></td>
                <td>{device.gateId}</td>
                <td>{formatDate(device.lastHeartbeat)}</td>
                <td>{device.appVersion || '--'}</td>
                <td>{device.modelVersion || '--'}</td>
                <td>{device.pendingAttendanceSync ?? '--'}</td>
                <td><button onClick={() => onOpenDevice(device.deviceId)}>Details</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h3 style={{ marginTop: 16 }}>Offline Devices</h3>
      <p className="muted">{offlineDevices.length} offline device(s) across visible support scope.</p>
    </section>
  );
}

function TodaySummaryPanel({ summary, school }: { summary: TodaySummary | null; school: SchoolHealth | null }) {
  if (!summary) return <section className="card"><p>Select a school and load today summary.</p></section>;
  return (
    <section className="card">
      <h2>Today Summary {school ? `· ${school.schoolName}` : ''}</h2>
      <div className="grid">
        <Stat label="Total marked" value={summary.totalMarked} />
        <Stat label="Present" value={summary.present} />
        <Stat label="Late" value={summary.late} />
        <Stat label="Half day" value={summary.halfDay} />
      </div>
      <div className="table-wrap" style={{ marginTop: 16 }}>
        <table>
          <thead><tr><th>Student</th><th>Class</th><th>Status</th><th>In</th><th>Out</th></tr></thead>
          <tbody>{summary.recent.map((row) => <tr key={`${row.erpStudentId}-${row.inTime}`}><td>{row.name}<br /><span className="muted">{row.erpStudentId}</span></td><td>{row.className || '--'}</td><td>{row.status}</td><td>{formatDate(row.inTime)}</td><td>{formatDate(row.outTime)}</td></tr>)}</tbody>
        </table>
      </div>
    </section>
  );
}

function FailedRecognitionsPanel({ failed }: { failed: FailedRecognitions | null }) {
  if (!failed) return <section className="card"><p>Select a school and load failed recognitions.</p></section>;
  return (
    <section className="card">
      <h2>Failed Recognitions</h2>
      <p>Total: <b>{failed.total}</b> · Liveness failures: <b>{failed.livenessFailureCount}</b></p>
      <div className="table-wrap"><table><thead><tr><th>Time</th><th>Device</th><th>Gate</th><th>Reason</th><th>Quality</th><th>Liveness</th></tr></thead><tbody>{failed.logs.map((log) => <tr key={log.id}><td>{formatDate(log.timestamp)}</td><td>{log.deviceId}</td><td>{log.gateId || '--'}</td><td>{log.reason}</td><td>{formatNumber(log.qualityScore)}</td><td>{formatNumber(log.livenessScore)}</td></tr>)}</tbody></table></div>
    </section>
  );
}

function SyncHealthPanel({ syncHealth }: { syncHealth: SyncHealth | null }) {
  if (!syncHealth) return <section className="card"><p>Select a school and load ERP sync status.</p></section>;
  return (
    <section className="grid">
      <Stat label="Pending ERP sync" value={syncHealth.pendingErpSync} tone={syncHealth.pendingErpSync > 0 ? 'warn' : 'pass'} />
      <Stat label="Synced ERP records" value={syncHealth.syncedErpSync} />
      <Stat label="Failed ERP sync" value={syncHealth.failedErpSync} tone={syncHealth.failedErpSync > 0 ? 'fail' : 'pass'} />
      <article className="card"><h3>Last attendance sync</h3><p>{formatDate(syncHealth.lastAttendanceSync)}</p></article>
    </section>
  );
}

function DeviceDetailPanel({ deviceId, logs }: { deviceId: string; logs: DeviceLogsSummary | null }) {
  if (!logs) return <section className="card"><p>Open a device to view log summary.</p></section>;
  return (
    <section className="card">
      <h2>Device Detail</h2>
      <p className="muted">{deviceId}</p>
      <p>Total logs: <b>{logs.totalLogs}</b> · Last log: <b>{formatDate(logs.lastLogAt)}</b></p>
      <div className="grid">{Object.entries(logs.counts).map(([action, count]) => <Stat key={action} label={action} value={count} />)}</div>
      <div className="table-wrap" style={{ marginTop: 16 }}><table><thead><tr><th>Time</th><th>Action</th><th>Entity</th><th>ID</th></tr></thead><tbody>{logs.recent.map((log, index) => <tr key={`${log.createdAt}-${index}`}><td>{formatDate(log.createdAt)}</td><td>{log.action}</td><td>{log.entityType}</td><td>{log.entityId || '--'}</td></tr>)}</tbody></table></div>
    </section>
  );
}

function Stat({ label, value, tone }: { label: string; value: number | string; tone?: 'pass' | 'warn' | 'fail' }) {
  return <article className="card"><h3>{label}</h3><p className={tone ? `status-${tone}` : undefined}>{value}</p></article>;
}

function formatDate(value: string | null | undefined) {
  if (!value) return '--';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function formatNumber(value: number | null | undefined) {
  return typeof value === 'number' ? value.toFixed(3) : '--';
}
