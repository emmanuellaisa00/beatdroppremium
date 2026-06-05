# BeatDrop sideload production hardening plan

BeatDrop intentionally ships as a sideload app and keeps extraction features.
That means Play Store policy is not the primary constraint, but production still
requires stability, security, diagnostics, update velocity, and clean boundaries.

## Non-negotiable architecture boundaries

- Local playback/library must work even when every extractor is broken.
- Extraction code must sit behind `ExtractionEngine`.
- Downloads must converge to one persistent, Room-backed queue.
- Debug logs must redact stream URLs, signatures, tokens, cookies, and headers.
- Remote config must be able to disable or reorder resolver strategies.
- APK updates must be verified by SHA-256 before install.

## Current hardening shipped

- `PlayerViewModel.connect()` is idempotent to prevent duplicate collectors.
- Download-complete playback intents use an event channel instead of a polling loop.
- Glass tilt highlights share one composition-local sensor listener in the app shell.
- DebugLog sanitizes URLs/tokens before storing or exporting diagnostics.
- Extraction boundary interface added for incremental resolver isolation.
- Sideload remote-config and update-manifest models added.
- Removed stale `.orig` and patch artifacts from the repo.
- Removed unnecessary sensitive manifest permissions (`READ_CLIPBOARD`, `SYSTEM_ALERT_WINDOW`, `READ_MEDIA_IMAGES`).

## Next hardening milestones

1. Wire `SideloadRemoteConfig` to a signed JSON endpoint.
2. Add in-app update checker using `UpdateManifest` with SHA-256 verification.
3. Migrate callers from direct `YoutubeService` / `OnlineSearch` to `ExtractionEngine`.
4. Replace the dual DownloadManager/V2 architecture with one persistent manager.
5. Move downloads/playlists/play-counts/search history/features from JSON DataStore to Room.
6. Split `PlayerViewModel` into playback, search, download, lyrics, settings, and automix controllers.
7. Add crash reporting and production-safe diagnostic export.
