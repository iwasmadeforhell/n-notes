# N.Notes

Minimal Android app: to-do list with calendar, and a gym tracker (sets, reps, weight).
Data is stored on the phone only. The app checks the latest GitHub release on start and offers to update itself.

Build: `powershell -File build.ps1 -Version 1.1.0 -Code 2` (needs JDK 17 and the Android build-tools/platform in `.sdk\x`).
Release: attach `N.Notes.apk` and `N.Notes.apk.sha256` to a GitHub release tagged `vX.Y.Z`. Always sign with the same keystore.
