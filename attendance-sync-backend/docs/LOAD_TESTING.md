# Backend Load Testing

The backend includes a dependency-free Node load runner for attendance event sync. It uses the same REST API that Android devices use and should be run with the mock ERP adapter so ERP availability does not affect attendance ingest results.

## Setup

```bash
cp .env.example .env
docker compose up -d postgres
npx prisma migrate deploy
npm run seed:staging
npm run start:dev
```

Use a seeded device token from `npm run seed:staging`.

```bash
export LOAD_TEST_BASE_URL=http://localhost:3000
export LOAD_TEST_SCHOOL_ID=local-staging-school
export LOAD_TEST_DEVICE_ID=GATE-STAGING-0001
export LOAD_TEST_DEVICE_TOKEN=<seeded-token>
export ERP_ADAPTER=mock
npm run load:test
```

## Scenarios

`npm run load:test` runs all scenarios by default:

1. One school, one device, 1000 attendance events.
2. One school, 10 logical devices, 6000 attendance events.
3. 100 schools, 2 logical devices each smoke pattern.
4. Duplicate event storm.
5. Offline sync burst where devices upload pending events together.

Run one scenario with:

```bash
LOAD_TEST_SCENARIO=duplicate-storm npm run load:test
```

Supported values are `one-device`, `ten-devices`, `hundred-schools`, `duplicate-storm`, `offline-burst`, and `all`.

## Metrics

The script reports:

- request count
- accepted/rejected event count
- duplicate count
- error rate
- p50 latency
- p95 latency
- p99 latency
- max latency

Database CPU/memory should be captured separately from Docker or cloud metrics:

```bash
docker stats
```

## Acceptance Targets

- Duplicate events are returned as duplicate/already synced and do not create extra attendance records.
- A batch does not fail completely because one event is duplicate or bad.
- p95 latency should be acceptable for staging traffic and reviewed before pilot expansion.
- Cross-school data leakage must remain zero; JWT school/device scoping should reject mismatched requests.
- Use `ERP_ADAPTER=mock`; real ERP is not required for load testing.

## Notes

The multi-school scenario uses the current seeded token and is intended as an API pressure smoke unless a richer multi-school seed is added. For true multi-school isolation load tests, seed per-school devices and run each batch with that school's token.
