# Android Stress Testing

The Android app includes a debug-only stress testing screen for basic-device reliability checks. It does not export face images or raw embeddings.

## How To Run

1. Install a debug build on the target phone.
2. Register/bind the device to a test school and gate.
3. Open `Android Stress Test` from the debug home screen.
4. Start Gate Mode on the device for a realistic camera workload, then run the stress monitor for 30 minutes.
5. Export the CSV and attach it to the pilot test report.

## Metrics Captured

- camera analyzer FPS
- processed FPS
- dropped frames
- average decision time
- memory usage estimate
- sync queue size
- battery percent
- Android thermal status when available
- warning flags

## Warning Rules

The debug screen raises warnings when:

- analyzer FPS drops below 8 FPS after the camera has started
- average decision time exceeds 3 seconds
- Android reports severe/critical/emergency thermal state
- memory grows by more than 150 MB during the run
- pending sync queue grows by more than 100 items
- battery drops below 20%

## Acceptance Guidance

For a pilot-ready basic Android device:

- no UI freeze during the 30-minute run
- analyzer FPS remains stable enough for live recognition
- average decision time stays below 3 seconds
- dropped frames do not grow continuously
- memory does not steadily climb
- pending sync queue remains bounded or drains when network is available
- no severe thermal warning during normal gate placement

## Privacy

Stress CSV exports include metrics only. They must not include face images, camera frames, raw embeddings, or student photos.
