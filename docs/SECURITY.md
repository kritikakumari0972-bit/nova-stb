# Security model

NovaSTB uses ADB only after the STB has already enabled the service and authorized the app's ADB identity.

## Safeguards in this alpha

- The RSA ADB key is stored in the app's private no-backup directory.
- The app does not request broad storage permissions; Android's document picker grants access to individual files.
- The remote file browser is restricted to shared storage rooted at /sdcard.
- Remote paths are normalized and traversal outside /sdcard is rejected.
- The app does not expose an arbitrary shell/terminal screen.
- Uninstall is limited to packages returned by Package Manager's third-party app list and requires an in-app confirmation.
- Plain TCP ADB has a separate acknowledgement warning.
- No network port is opened by NovaSTB; the app connects outbound to the address and port entered by the user.

## Network warning

Legacy ADB over TCP is not encrypted. Only use it on a trusted private network. Do not port-forward ADB through a router or expose it to the public internet. Prefer TLS wireless debugging if the STB firmware supports it.

## Out of scope

No root escalation, bootloader unlocking, firmware flashing, operator-management service changes, credential collection or ADB activation exploit is included.
