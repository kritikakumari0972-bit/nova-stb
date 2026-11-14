# Device compatibility

## Target devices

| Device | Intended role | Status |
| --- | --- | --- |
| Motorola G35 5G | Host phone running NovaSTB | Android app target; not yet tested on physical hardware |
| Jio JMSB200A | Remote Android STB | Community reports describe network-ADB support; not tested by this project |

## Connection prerequisites

NovaSTB is an ADB client. It does not turn on ADB or establish a first-time trust relationship unless the STB presents a supported ADB pairing or authorization flow.

For TCP mode:
1. The STB must already have an ADB daemon listening on the entered LAN address and port.
2. The phone and STB must be able to reach each other on the same trusted network.
3. The user must accept the ADB RSA authorization prompt on the TV if the device asks for it.
4. Plain TCP ADB traffic is not encrypted.

For TLS wireless debugging:
1. The target must expose Android's wireless-debugging pairing service.
2. Enter the pairing port and six-digit code shown by the target.
3. After pairing, enter the separate connection port displayed by the target.

Some vendor STB firmware exposes only legacy ADB TCP, not Android's TLS wireless-pairing UI. In that case the TLS flow will not work.

## File access

The first release deliberately browses only /sdcard shared storage. ADB permissions and the target Android version determine which entries are visible. App-private directories and protected partitions are outside the feature scope.

## Validation still required

- Build and CI pass.
- Install and first-launch on a Motorola G35 5G.
- Successful user-authorized ADB connection to JMSB200A.
- Confirm APK install result and package-list refresh.
- Upload and download a small file, then compare hashes.
- Check folder navigation, spaces and apostrophes in file names.
- Confirm app uninstall is limited to third-party packages.
