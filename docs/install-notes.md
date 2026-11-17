# AA Booter — install notes

## Platform signing (StarWin11)

Official AOSP test keys only (`platform.pk8` + `platform.x509.pem` from `platform/build` `target/product/security/`) were converted locally to a JKS **outside** the repo:

- Key dir: `C:\dev\keys\aosp-platform\` (`platform.jks` + source files; never commit)
- Local config: gitignored `keystore.properties` (from `keystore.properties.example`)

Verified fingerprints (JKS and release APK):

- SHA-1: `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA`
- SHA-256: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`

Signed release APK (local build artifact; gitignored `*.apk`):

`C:\dev\proj\aa-booter\app\build\outputs\apk\release\app-release.apk`

## Install on the head unit

1. Install with the package installer (same path as `com.miniaa.launcher`), not as a priv-app:

   ```text
   adb install -r app\build\outputs\apk\release\app-release.apk
   ```

2. Open **AA Booter**. The screen shows whether the head unit server is already running (it checks for a listener on port 5277).
3. **Head unit server** turns the server on or off now. **Start when the head unit boots** is on by default; turn it off to skip the next boot.
4. **Check for updates** looks at the latest GitHub release and can download `aa-booter.apk` for the package installer.
5. Logs: `adb logcat -s AaBooter` — look for start OK or `SecurityException`.

## Rebuild on this machine

```powershell
# keystore.properties must already point at C:\dev\keys\aosp-platform\platform.jks
.\gradlew.bat assembleRelease
```

Do not use the debug keystore. Do not commit `platform.*`, `.p12`, `.jks`, or `keystore.properties`.
