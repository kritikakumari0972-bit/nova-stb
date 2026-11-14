# Roadmap

## Phase 1 — phone-first core (current)
- [x] Native Android project and build workflow.
- [x] TCP ADB client connection.
- [x] Wireless Debugging TLS pairing/client path.
- [x] Single APK installation.
- [x] Shared-storage browse/upload/download.
- [x] Third-party app list, launch and uninstall.
- [x] Device properties.
- [x] Remote path safety tests.
- [ ] Physical test on Motorola G35 5G.
- [ ] Physical connection test on Jio JMSB200A.
- [ ] Successful end-to-end APK install and file round-trip.

## Phase 2 — transfer and installer quality
- [ ] Streaming transfer progress and cancellation.
- [ ] Folder upload/download with a bounded queue.
- [ ] Resume/retry for interrupted transfers.
- [ ] Split APK (.apks) installer with bundle validation.
- [ ] XAPK support including optional OBB payload handling.
- [ ] APK metadata: package name, version, min SDK and ABI.
- [ ] Transfer history and integrity checks.

## Phase 3 — device experience
- [ ] Remember multiple STB profiles.
- [ ] Local network discovery with clear user consent and bounded scanning.
- [ ] Storage usage view and file sorting.
- [ ] App icons and human-readable app labels where obtainable.
- [ ] Export installed APKs where Package Manager permits it.
- [ ] Optional TV remote controls only through documented public APIs.

## Explicit non-goals
- No firmware replacement, root or bootloader unlock.
- No bypass of operator management, DRM or app licensing.
- No claim of Google certification or Play Store compatibility.
