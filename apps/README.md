# Ready-made apps

Built or verified on 19–20 October 2026 for a Xiaomi MiTV-MOSR1 (Android TV 11, 32-bit `armeabi-v7a`).
See [`../SETUP-NITESH-TV.md`](../SETUP-NITESH-TV.md) for how to install them.

| File | What it is | SHA-256 |
|---|---|---|
| `tvhome-launcher-nitesh.apk` | The home screen from `launcher/` (branch `nitesh-tv`): dock YouTube, Xiaomi TV+, Netflix, Prime Video; animated "Welcome Nitesh", weather, Sleep button, pen-written screensaver name. Signed with this PC's debug key. | `8854cf2e3993ca5c9acde1bf449230d8ff4485e8724e06b753f1f1475165d802` |
| `JTV-Preview-2.0-beta.22-clean.apk` | JTV v2.0-beta.22 with our clean black UI, the 3-across "Recent" grid of most-watched channels, and **all channels played only through Jio** (third-party restream code removed). App ID `com.fenyx.jtv.preview`, name "JTV Preview". Signed with a local key that is **not** in this repo. | `b6f7eb32ea356e2ca510e2833c16c690ca85c07dd00c67954660882421ac7d85` |
| `Flow-3.0.0-TV-armeabi-v7a-FOSS.apk` | Flow 3.0.0, ad-free YouTube / YouTube Music with a TV interface, official FOSS 32-bit build, unmodified. | `0bf9dcb6f01bcde2ba880cf014ee65589aa9e911f417fda456c3f7019ce34454` |

## Notes

- **Launcher APK**: installing the APK alone does not make it the home screen. Use
  `launcher\build-and-install.ps1` (it also disables the stock launcher and turns on the Sleep service),
  or follow step 5 of the setup guide. Rebuild it if you change the name, dock or config.
- **JTV Preview** is a separate app from upstream JTV, so it needs its own one-time Jio OTP sign-in.
  Use **Sign in with Jio number**, not "Connect with a code" (that uses the developer's server).
  Not yet tested on the TV after the Jio-only change: check that Sony channels play.
  It cannot be updated over itself from another PC unless that PC has the same signing key; rebuild from
  [`../jtv`](../jtv) and uninstall the old one first if needed.
- **Flow FOSS** has no in-app updater. Update by installing a newer release from
  <https://github.com/Flow-Tube/Flow/releases> (check the signing certificate SHA-256
  `43:22:29:4E:D4:CA:A2:D4:29:41:40:09:58:18:08:0F:FE:8A:CC:1F:BE:3C:DC:76:10:7D:F4:5C:52:86:BE:40`).

## Licences

- Flow is GPL-3.0. Source: <https://github.com/Flow-Tube/Flow> (tag `v3.0.0`). This file is the unmodified
  official release asset `flow-foss-armeabi-v7a.apk`.
- JTV is Apache-2.0 (<https://github.com/F-e-n-y-x/JioTV-AndroidTV->). Licence and notice are in
  [`../jtv`](../jtv); our changes are the patch in that folder.
- The launcher is MIT (this repository, forked from Gathin23/tv-vibehack). The Outfit font is OFL.
