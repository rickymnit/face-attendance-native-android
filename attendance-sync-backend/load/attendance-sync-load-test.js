#!/usr/bin/env node
const { performance } = require('node:perf_hooks');

const BASE_URL = process.env.LOAD_TEST_BASE_URL || 'http://localhost:3000';
const TOKEN = process.env.LOAD_TEST_DEVICE_TOKEN || '';
const SCHOOL_ID = process.env.LOAD_TEST_SCHOOL_ID || 'school-demo';
const DEVICE_ID = process.env.LOAD_TEST_DEVICE_ID || 'GATE-DEVICE-00042';
const GATE_ID = process.env.LOAD_TEST_GATE_ID || 'main-gate';
const BATCH_SIZE = Number.parseInt(process.env.LOAD_TEST_BATCH_SIZE || '50', 10);
const CONCURRENCY = Number.parseInt(process.env.LOAD_TEST_CONCURRENCY || '8', 10);
const SCENARIO = process.env.LOAD_TEST_SCENARIO || 'all';

if (!TOKEN) {
  console.error('LOAD_TEST_DEVICE_TOKEN is required. Use a local staging seeded device token.');
  process.exit(1);
}

function percentile(values, p) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
}

function dateOnly(date) {
  return date.toISOString().slice(0, 10);
}

function eventFor({ eventId, schoolId = SCHOOL_ID, deviceId = DEVICE_ID, gateId = GATE_ID, index }) {
  const timestamp = new Date(Date.UTC(2026, 6, 20, 8, index % 50, index % 59));
  return {
    eventId,
    schoolId,
    studentId: `LOAD-STU-${String(index % 6000).padStart(5, '0')}`,
    erpStudentId: `LOAD-STU-${String(index % 6000).padStart(5, '0')}`,
    deviceId,
    gateId,
    eventType: 'PRESENT',
    attendanceDate: dateOnly(timestamp),
    timestamp: timestamp.toISOString(),
    matchScore: 0.93,
    livenessScore: 0.88,
    qualityScore: 0.9,
    modelVersion: 'load-test-model-v1',
    recognitionMode: 'Strict',
  };
}

async function postBatch(events, schoolId = SCHOOL_ID, deviceId = DEVICE_ID, gateId = GATE_ID) {
  const started = performance.now();
  const response = await fetch(`${BASE_URL}/api/attendance/events/sync`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      authorization: `Bearer ${TOKEN}`,
      'x-schoollog-device-id': deviceId,
      'x-schoollog-school-id': schoolId,
      'idempotency-key': events.map((event) => event.eventId).join(','),
    },
    body: JSON.stringify({ schoolId, deviceId, gateId, events }),
  });
  const latency = performance.now() - started;
  const bodyText = await response.text();
  let body = null;
  try { body = bodyText ? JSON.parse(bodyText) : null; } catch (_) { body = { raw: bodyText }; }
  return { ok: response.ok, status: response.status, latency, body };
}

async function runQueue(tasks) {
  let cursor = 0;
  const results = [];
  async function worker() {
    while (cursor < tasks.length) {
      const task = tasks[cursor++];
      results.push(await task());
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  return results;
}

function batches(totalEvents, prefix, schoolId = SCHOOL_ID, deviceId = DEVICE_ID, gateId = GATE_ID) {
  const tasks = [];
  for (let offset = 0; offset < totalEvents; offset += BATCH_SIZE) {
    const events = [];
    for (let i = offset; i < Math.min(totalEvents, offset + BATCH_SIZE); i += 1) {
      events.push(eventFor({ eventId: `${prefix}-${i}`, schoolId, deviceId, gateId, index: i }));
    }
    tasks.push(() => postBatch(events, schoolId, deviceId, gateId));
  }
  return tasks;
}

function summarize(name, results) {
  const latencies = results.map((result) => result.latency);
  const accepted = results.reduce((sum, result) => sum + (result.body?.accepted?.length || 0), 0);
  const rejected = results.reduce((sum, result) => sum + (result.body?.rejected?.length || 0), 0);
  const duplicates = results.reduce((sum, result) => sum + (result.body?.accepted || []).filter((item) => String(item.status).includes('duplicate')).length, 0);
  const errors = results.filter((result) => !result.ok).length;
  return {
    name,
    requests: results.length,
    accepted,
    rejected,
    duplicates,
    errorRate: results.length ? errors / results.length : 0,
    p50LatencyMs: percentile(latencies, 50),
    p95LatencyMs: percentile(latencies, 95),
    p99LatencyMs: percentile(latencies, 99),
    maxLatencyMs: Math.max(0, ...latencies),
  };
}

async function scenarioOneSchoolOneDevice() {
  return summarize('one school, one device, 1000 events', await runQueue(batches(1000, `s1-${Date.now()}`)));
}

async function scenarioOneSchoolTenDevices() {
  const tasks = [];
  for (let d = 0; d < 10; d += 1) {
    tasks.push(...batches(600, `s2-d${d}-${Date.now()}`, SCHOOL_ID, DEVICE_ID, GATE_ID));
  }
  return summarize('one school, ten logical devices, 6000 events', await runQueue(tasks));
}

async function scenarioHundredSchools() {
  const tasks = [];
  for (let s = 0; s < 100; s += 1) {
    for (let d = 0; d < 2; d += 1) {
      tasks.push(...batches(10, `s3-school${s}-device${d}-${Date.now()}`, SCHOOL_ID, DEVICE_ID, GATE_ID));
    }
  }
  return summarize('100 schools x 2 logical devices smoke', await runQueue(tasks));
}

async function scenarioDuplicateStorm() {
  const duplicateId = `duplicate-storm-${Date.now()}`;
  const event = eventFor({ eventId: duplicateId, index: 1 });
  const tasks = Array.from({ length: 100 }, () => () => postBatch([event]));
  return summarize('duplicate event storm', await runQueue(tasks));
}

async function scenarioOfflineBurst() {
  const tasks = [];
  for (let d = 0; d < 20; d += 1) {
    tasks.push(...batches(100, `offline-burst-d${d}-${Date.now()}`, SCHOOL_ID, DEVICE_ID, GATE_ID));
  }
  return summarize('offline sync burst', await runQueue(tasks));
}

async function main() {
  const all = [
    ['one-device', scenarioOneSchoolOneDevice],
    ['ten-devices', scenarioOneSchoolTenDevices],
    ['hundred-schools', scenarioHundredSchools],
    ['duplicate-storm', scenarioDuplicateStorm],
    ['offline-burst', scenarioOfflineBurst],
  ];
  const selected = SCENARIO === 'all' ? all : all.filter(([name]) => name === SCENARIO);
  if (!selected.length) throw new Error(`Unknown LOAD_TEST_SCENARIO=${SCENARIO}`);
  console.log(JSON.stringify({ baseUrl: BASE_URL, batchSize: BATCH_SIZE, concurrency: CONCURRENCY, scenario: SCENARIO }, null, 2));
  const summaries = [];
  for (const [, fn] of selected) {
    const summary = await fn();
    summaries.push(summary);
    console.log(JSON.stringify(summary, null, 2));
  }
  const failed = summaries.some((summary) => summary.errorRate > 0.02);
  process.exit(failed ? 2 : 0);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
