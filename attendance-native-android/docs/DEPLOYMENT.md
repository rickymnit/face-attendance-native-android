# Schoollog Gate Device Deployment Guide

## Purpose

This guide prepares an Android phone for Schoollog AI Gate Attendance in a real school-gate kiosk setup. The app is designed to keep attendance offline-first, use live CameraX `Preview + ImageAnalysis`, and avoid photo capture during normal attendance.

## Pre-Deployment Checklist

- Install the correct app build for the environment.
- Confirm `API_BASE_URL` points to the intended backend environment.
- Confirm the face embedding model asset is installed and matches backend `modelVersion`.
- Register/bind the device to one school and one gate using the first-run setup screen.
- Run `Sync Students/Embeddings Now` after registration.
- Verify Settings shows the correct school, device ID, gate ID, model version, and embedding sync version.
- Test Gate Mode with at least 10 enrolled students before morning entry.

## Physical Mounting

- Mount the phone securely on a stand or fixed bracket.
- Keep the front camera roughly at student face height.
- Keep the camera stable; vibration can affect stability and liveness checks.
- Place the phone where one student naturally stands 40-70 cm from the camera.
- Avoid direct sunlight into the camera where possible.
- Keep a charger connected for the full attendance window.

## Gate Mode Operation

Gate Mode is built for kiosk-style use:

- Full-screen live front-camera preview.
- Large status/result message.
- Green result state when attendance is marked.
- Red result state for manual review, duplicate, or failed outcome.
- Pending sync count visible at the top and bottom result panel.
- Network state visible in the header.
- Device/gate name and current time visible in the header.
- Wake behavior keeps the screen on while Gate Mode is active.

System back is disabled in Gate Mode to prevent accidental exits. Use the on-screen `Exit` action and confirmation dialog for setup/support work.

## Battery And Charging

Gate Mode shows `Connect charger` when battery is low and the phone is not charging.

Recommended practice:

- Keep the phone plugged in during school entry and exit windows.
- Use a high-quality charger and cable.
- Avoid power-saving modes that throttle camera or background sync.
- Verify charging before the morning rush.

## Network And Offline Mode

The app can mark attendance offline. When the network is unavailable, Gate Mode shows:

```text
Offline mode active, attendance will sync later
```

Operational notes:

- Attendance is saved locally first.
- Pending sync count should increase while offline.
- WorkManager sync retries after network returns.
- Staff can tap `Sync Now` in Settings when internet is restored.
- Student/embedding updates require network; use `Sync Students/Embeddings Now` after reconnecting.

## Android Screen Pinning

Screen pinning is the simplest kiosk preparation and does not require device-owner APIs.

Typical Android steps:

1. Open Android Settings.
2. Go to Security or Security & privacy.
3. Enable Screen pinning or App pinning.
4. Open Schoollog Attendance.
5. Enter Gate Mode.
6. Open recent apps.
7. Tap the app icon or menu.
8. Choose Pin.

Exact labels vary by device manufacturer.

Recommended policy:

- Use screen pinning for pilots and early deployments.
- Train support staff how to unpin using the device-specific button gesture/PIN.
- Keep the phone physically mounted and supervised.

## Android Lock Task Mode Preparation

Managed lock task mode is stronger than screen pinning but usually requires mobile device management or device-owner provisioning. Do not force device-owner APIs inside the app unless the school deployment requires managed kiosk mode.

For production fleets, consider:

- Android Enterprise device-owner enrollment.
- MDM-managed allowed apps list.
- Restrict notification shade and system settings access.
- Disable app uninstall for guard users.
- Enforce charging, Wi-Fi, time, and OS update policies.

The app is prepared for kiosk operation through full-screen Gate Mode, guarded exit, and wake behavior. A future MDM/device-owner integration can add managed lock task mode without changing attendance recognition logic.

## Support Exit And Reconfiguration

Normal guards should not switch schools or gates casually.

Support/admin flow:

- Use the confirmed `Exit` action to leave Gate Mode.
- Open Settings to review current binding.
- In debug/support builds, use `Unbind Device` only when intentionally reprovisioning.
- Rebind using the setup token from Schoollog backend/support.

Release builds should expose unbinding only through an approved admin/support workflow.

## Daily Startup Procedure

1. Connect charger.
2. Open Schoollog Attendance.
3. Confirm the device is bound to the correct school and gate.
4. Tap `Sync Students/Embeddings Now` if new enrollments were added.
5. Tap `Sync Now` if pending attendance exists from a previous offline session.
6. Start Gate Mode.
7. Confirm network/battery status is acceptable.
8. Run a quick test scan with a known enrolled student.

## End Of Day Procedure

1. Check pending sync count.
2. Reconnect internet if offline.
3. Tap `Sync Now` if needed.
4. Confirm pending sync count reaches zero or record remaining failures.
5. Keep the phone charging for the next session.

## Production Readiness Notes

Before production rollout:

- Validate recognition thresholds on real school data.
- Validate liveness with real spoof tests.
- Confirm no false attendance in pilot testing.
- Confirm attendance persists through app restart while offline.
- Confirm backend accepts idempotent attendance sync without duplicates.
- Confirm embedding delta sync does not remove attendance events.
