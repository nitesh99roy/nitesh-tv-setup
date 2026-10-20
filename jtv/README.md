# JTV Preview: our changes to JTV v2

Our changes on top of the official JTV **v2.0-beta.22** (<https://github.com/F-e-n-y-x/JioTV-AndroidTV->,
Apache-2.0). The ready-built app is [`../apps/JTV-Preview-2.0-beta.22-clean.apk`](../apps).

| File | What it is |
|---|---|
| `jtv-clean-ui.patch` | All our changes except one file: black UI, soft-white accent, Outfit font, rounder focus, darker player card, 3-across "Recent" grid of the 12 most-watched channels (watch-time tracking), PC preview renders, and the `-PjtvPreview` build switch. |
| `ZeeRepository.kt` | Replaces upstream's `android/app/src/main/java/com/fenyx/jtv/data/ZeeRepository.kt`. Upstream sends Sony, Zee and Star channels to third-party restream servers and other people's Jio cookies and keys; this stand-in turns that off so every channel plays through Jio under your own login. |
| `LICENSE-JTV-Apache-2.0.txt`, `NOTICE-JTV.txt` | JTV's licence and notice. |

## Rebuild (Windows, PowerShell)

Needs Android Studio (for its JDK 21 and the Android SDK).

```powershell
git clone https://github.com/F-e-n-y-x/JioTV-AndroidTV-.git JTV
cd JTV
git checkout -b nitesh-clean-ui v2.0-beta.22
git apply --binary ..\jtv\jtv-clean-ui.patch
Copy-Item ..\jtv\ZeeRepository.kt android\app\src\main\java\com\fenyx\jtv\data\ZeeRepository.kt -Force
Remove-Item android\app\src\test\java\com\fenyx\jtv\data\ZeeRepositoryTest.kt
cd android
"sdk.dir=$("$env:LOCALAPPDATA\Android\Sdk" -replace '\\','\\')" | Out-File -Encoding ascii local.properties
```

Signing: make your own key once (keep it private, never commit it):

```powershell
$pw = Read-Host "Choose a keystore password"
keytool -genkeypair -keystore jtv-release.keystore -alias jtv -keyalg RSA -keysize 2048 -validity 10000 -storepass $pw -keypass $pw -dname "CN=JTV local build"
"storeFile=jtv-release.keystore`nstorePassword=$pw`nkeyAlias=jtv`nkeyPassword=$pw" | Out-File -Encoding ascii keystore.properties
```

Build "JTV Preview" (separate app, `com.fenyx.jtv.preview`):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease -PjtvPreview
# -> app\build\outputs\apk\release\app-release.apk
```

Preview screens on the PC before installing (PNG files in `app\build\previews`):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*PreviewRenderTest*"
```

A build signed with a different key cannot update an installed JTV Preview: uninstall it first
(you will need to sign in with your Jio OTP again).
