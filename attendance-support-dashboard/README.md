# Schoollog Attendance Support Dashboard

Basic Next.js dashboard for Schoollog support/admin monitoring during attendance pilots. It calls the support APIs from `attendance-sync-backend`.

## Screens

- Login placeholder
- Schools health list
- Devices list by school
- Today attendance summary
- Failed recognitions
- ERP sync status
- Device detail/log summary

## Requirements

- Node.js 20+
- Running `attendance-sync-backend`
- Support APIs enabled from Prompt 58

## Run

```bash
cd attendance-support-dashboard
npm install
npm run dev
```

Open `http://localhost:3000` or the port printed by Next.js.

If the backend is also running on port `3000`, start the dashboard on another port:

```bash
npm run dev -- -p 3001
```

## Login Placeholder

The dashboard currently uses a placeholder login form. Enter:

- Backend API URL, for example `http://localhost:3000`
- Role: `SCHOOLLOG_SUPPORT`, `SCHOOL_ADMIN`, or `SUPER_ADMIN`

The selected role is sent as:

```http
X-Schoollog-Support-Role: SCHOOLLOG_SUPPORT
```

This matches the backend role placeholder. Dashboard requests go through a same-origin Next.js proxy at `/api/backend/*`, which forwards to the configured backend URL and avoids browser CORS issues during local pilot support. Replace this with real admin authentication before production use.

## Notes

- The dashboard does not display face images.
- The dashboard does not fetch or display raw embeddings.
- It is intentionally simple for pilot support workflows.
