# AA Booter — install notes

## Blocker: platform key missing on this machine

Searched for `platform.jks` / `platform.keystore` / `platform.pk8` / `platform.x509.pem` under common roots (`C:\Users\caleb`, `C:\dev`, `D:\`, `E:\`, agent stores). **Not found.**

Expected signer SHA-256: `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`

Per product rules: do not download a keystore, do not commit key material, do not sign with the debug keystore. Signed APK was **not** built.

## After the platform key is available

1. Verify SHA-256 with `keytool -list -v -keystore <file>`.
2. Copy `keystore.properties.example` → `keystore.properties` and fill paths/passwords.
3. `.\gradlew.bat assembleRelease`
4. Install with the package installer (same path as `com.miniaa.launcher`), not as a priv-app:
   `adb install -r app\build\outputs\apk\release\app-release.apk`
5. Open **AA Booter** once and tap **Start head unit server** (clears stopped state for boot receivers; also the no-reboot test).
6. Next ACC/boot: confirm Android Auto head-unit-server notification and that `127.0.0.1:5277` accepts a connection before Open Headunit starts.
7. Logs: `adb logcat -s AaBooter` — look for start OK or `SecurityException`.
