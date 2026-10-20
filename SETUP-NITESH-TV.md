# Nitesh's TV setup: repeat it on another TV

Everything done to the Xiaomi **MiTV-MOSR1** (Android TV 11) on 19–20 October 2026, written so it can be
repeated on another Android TV from a Windows PC. Commands are PowerShell, run from this repository's folder.

**End result:** no PatchWall, ads or Xiaomi tracking; an Apple TV-style home screen (dock, animated
"Welcome Nitesh", weather, Sleep button, clock screensaver); Xiaomi TV+ kept; ad-free YouTube (Flow) and
a clean JioTV app (JTV Preview).

Everything below is reversible: apps are **disabled, never uninstalled** (see step 9).

---

## 1. On the TV

1. Settings → Device Preferences → About → press OK on **Build** seven times.
2. Back → **Developer options** → turn on **USB debugging** (no cable needed: on Xiaomi this also opens
   debugging over Wi-Fi).
3. Keep the TV on, on the same Wi-Fi as the PC.

## 2. On the PC (once)

Needs Python 3, and Android Studio for building the launcher.

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
```

Find the TV's address (it shows under Settings → Network, or scan for the debug port):

```powershell
# scans 192.168.0.x for the TV debug port (change the prefix if your Wi-Fi uses another range)
tools\venv\Scripts\python.exe -I -c "import socket,concurrent.futures as cf;ips=['192.168.0.%d'%i for i in range(2,255)];up=lambda h:(lambda s:(s.settimeout(0.5),s.connect_ex((h,5555))==0)[1])(socket.socket());[print('TV at',h) for h,ok in zip(ips,cf.ThreadPoolExecutor(64).map(up,ips)) if ok]"
$env:TV_HOST = "192.168.0.93"   # <- your TV's address
tools\venv\Scripts\python.exe -I tools\tv.py --probe
```

The first connection shows **"Allow USB debugging?"** on the TV: tick *Always allow* and press OK.
The PC's key is created in `tools\adbkey\` (git-ignored; never upload it).

Tip: the TV can come back from standby on a new address. Reserve its address in the Wi-Fi router
(DHCP reservation) to stop that.

## 3. Back up first

```powershell
New-Item -ItemType Directory -Force backup\$env:TV_HOST | Out-Null
@("pm list packages -f -U","pm list packages -d","settings list system","settings list secure","settings list global","getprop") |
  ForEach-Object { $_ } | tools\venv\Scripts\python.exe -I tools\tv.py --batch | Out-File -Encoding utf8 backup\$env:TV_HOST\before.txt
```

`backup\` is git-ignored: it holds the TV's serial number and IDs.

## 4. Debloat

**Same model (MiTV-MOSR1):** use the tested list [`examples/xiaomi-mitv-mosr1/debloat.txt`](examples/xiaomi-mitv-mosr1/debloat.txt).
It disables PatchWall, Xiaomi analytics, promo and nag apps, the factory test launcher, YouTube Kids and
the duplicate Xiaomi SonyLIV. It **keeps Xiaomi TV+**, Live TV, HDMI and tuner services, the remote,
voice search, Chromecast, Miracast, updates, Gallery, Music and TV Lock.

**Other model:** start from the closest list in [`examples/`](examples/), and check every package on the
new TV before disabling it (see [`PROMPT.md`](PROMPT.md)). Never disable Live TV / input / remote / keyboard /
Play Store / Play Services packages.

```powershell
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost $env:TV_HOST examples\xiaomi-mitv-mosr1\debloat.txt
```

The two stage-3 lines (`tvrecommendations`, `backdrop`) only take effect after the new launcher is in
(step 5). **Wait 30 seconds after any disable before restarting**, and restart with
`tools\venv\Scripts\python.exe -I tools\tv.py "svc power reboot"`: an immediate reboot can lose the change.

Optional, frees about 400 MB: clear the saved data of the disabled apps (they are not uninstalled):

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py 'for p in $(pm list packages -d | cut -d: -f2); do pm clear --user 0 $p; done'
```

## 5. Home screen (launcher)

Personal settings are in [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
welcome name (`welcome_name`), dock apps and order (`dock_apps`), Live TV app (`live_tv_package`,
`com.mitv.livetv` on Xiaomi), weather city (`weather_city`, empty = the TV's own Wi-Fi location).

Build, install, make it Home, disable the stock launcher and turn on the Sleep timer service:

```powershell
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost $env:TV_HOST -SleepService
```

(No build tools? A ready build with Nitesh's settings is [`apps/tvhome-launcher-nitesh.apk`](apps/). Install it
as in step 6, then run:
`tools\venv\Scripts\python.exe -I tools\tv.py "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity; pm disable-user --user 0 com.google.android.tvlauncher"`.)

Clock screensaver with the pen-written name:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1"
```

What it has: dock of 4 apps, app grid, Inputs / Wi-Fi / Sleep (moon) / Settings buttons, animated
"Welcome Nitesh" with the glowing pen, weather (Open-Meteo, location rounded to ~1 km), Sleep timer
15 min – 2 h (puts the TV in standby; cancels if you turn the TV off yourself).

## 6. Apps

Install any APK from [`apps/`](apps/) (Google Play Protect may show a prompt on the TV; accept it within
two minutes, or run the install again):

```powershell
function Install-TvApk($apk) {
  tools\venv\Scripts\python.exe -I tools\b64push.py $apk /data/local/tmp/app.apk
  tools\venv\Scripts\python.exe -I tools\tv.py "pm install -r /data/local/tmp/app.apk; rm -f /data/local/tmp/app.apk"
}
Install-TvApk apps\Flow-3.0.0-TV-armeabi-v7a-FOSS.apk
Install-TvApk apps\JTV-Preview-2.0-beta.22-clean.apk
```

- **Flow** (ad-free YouTube and YouTube Music, TV interface). No Google sign-in; it learns what you
  watch. Switch it to the dark theme in its Settings.
- **JTV Preview** (JioTV, clean black UI, Recent = big tiles of your 12 most-watched channels). Sign in
  with **your Jio number and OTP**. Avoid "Connect with a code". Rebuild instructions: [`jtv/`](jtv/).

These APKs are 32-bit (`armeabi-v7a`). On a 64-bit TV they still install; for Flow you can use
the `arm64-v8a` build from its release page instead.

## 7. Quirks found on this TV

- Xiaomi sets animations to 60% speed (`animator_duration_scale 0.6`); the launcher compensates.
- In standby the TV drops off the network completely: turn it on with the remote before using the tools.
- Some ROMs answer the Home button with a setup or factory app; this one's factory test launcher is in the
  debloat list.
- Installing through the PC triggers a Play Protect scan, which can take a few minutes.

## 8. Code

- Launcher and tools: this repository, branch history `nitesh-tv` (forked from Gathin23/tv-vibehack, MIT).
- JTV changes: [`jtv/`](jtv/) (patch on top of official v2.0-beta.22).
- Flow: unmodified official release.

## 9. Undo everything

```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost $env:TV_HOST examples\xiaomi-mitv-mosr1\debloat.txt
```

This re-enables every disabled app and brings back the stock home screen. Note: it sets animations to
100%, while Xiaomi's factory value is 60%. Uninstall Flow / JTV Preview / the launcher from the TV's
Settings → Apps if you don't want them.
