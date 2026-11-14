# NovaSTB

**Phone-first Android companion for managing an Android-based set-top box over an already-authorized ADB connection.**

NovaSTB is being built for a Motorola G35 5G phone and the Jio JMSB200A set-top box. The app is designed to run on the phone itself; it does not require a PC-side ADB executable or desktop companion.

## Alpha 0.1 scope

- Connect to an ADB endpoint using TCP with the device's existing ADB authorization.
- Pair and connect using Android Wireless Debugging TLS when the target exposes that feature.
- Install one APK selected with Android's Storage Access Framework.
- Browse shared storage under /sdcard.
- Upload a phone file to the current STB folder.
- Download an STB file to a phone-selected location.
- List third-party packages, launch an app, and uninstall a listed user app.
- Read basic manufacturer, model, Android release and SDK properties.
- Build and run path-safety unit tests in GitHub Actions.

## What NovaSTB does not do

- It does not enable ADB on the STB, unlock its bootloader, root it, bypass JioOS restrictions or change firmware.
- It does not access other apps' private data or protected system partitions.
- It does not guarantee that Google Play Store, Google Play Services, DRM apps or every sideloaded APK will work on JioOS.
- It does not yet support split APK bundles (.apks, .apkm, .xapk), OBB expansion data, backups of app-private data or automatic network-wide device scanning.
- It cannot connect until the STB already exposes an ADB endpoint and the user accepts its authorization prompt on the TV.

## Important JMSB200A note

Community-maintained Jio STB documentation lists JMSB200A as a model with reported network-ADB support, but the published initial-activation workflow is separate from an ADB client and has PC-oriented steps. NovaSTB intentionally implements the client side only. Do not assume that installing NovaSTB will activate ADB on a stock box.

## Build

The project uses Android Gradle Plugin 9.3.0, Kotlin 2.4.21, Gradle 9.6.0, JDK 17, Android SDK 37, Jetpack Compose and adb-kt 1.0.0.

A GitHub Actions workflow installs Gradle and the Android SDK, runs unit tests, assembles a debug APK, runs lint and uploads the APK as a workflow artifact.

For local Android Studio use, open this repository as an Android Gradle project and select JDK 17 or newer. This repository currently invokes a provisioned Gradle distribution in CI rather than committing a Gradle Wrapper binary.

## Network safety

Plain TCP ADB is not encrypted. Use it only on a trusted private LAN, never expose the STB's ADB port to the public internet, and disconnect when finished. Prefer TLS wireless debugging when supported by the target. ADB authorization is still required.

## License

MIT. The ADB protocol dependency is a separate third-party project under its own license; see its repository and artifact metadata.
