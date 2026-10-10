# Release process

## Versions

`app/build.gradle.kts`: `versionCode = 2`, `versionName = "2.1.0"`. Increase `versionCode` for every upload to Play.

## Signing

Release builds are signed only with an upload key you provide. There is no fallback to the debug key: if the four values below are missing, `assembleRelease`, `bundleRelease` and `packageRelease` stop with an error.

| Name | Meaning |
| --- | --- |
| `FINANCES_KEYSTORE_PATH` | path to the upload keystore (`.jks`) |
| `FINANCES_KEYSTORE_PASSWORD` | keystore password |
| `FINANCES_KEY_ALIAS` | key alias |
| `FINANCES_KEY_PASSWORD` | key password |

Provide them as environment variables or in `keystore.properties` in the project root (see `keystore.properties.example`; the real file is git-ignored). Never commit keystores or passwords. Use Play App Signing; the key above is the upload key.

Creating an upload key:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

## Build

```bash
./gradlew clean testDebugUnitTest lint
./gradlew bundleRelease assembleRelease
```

Outputs: `app/build/outputs/bundle/release/app-release.aab` and `app/build/outputs/apk/release/app-release.apk`. They are build products and are not committed.

## Checks before uploading

```bash
# Signing certificate
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# Package, versions, SDK levels, debuggable flag
aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -5
aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/release/app-release.apk | grep -i debuggable
# Merged permissions
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
# 16 KB page size
scripts/check-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
bundletool dump config --bundle app/build/outputs/bundle/release/app-release.aab
```

Expect `package: name='com.teppe21.finances'`, `versionCode='2'`, `sdkVersion:'26'`, `targetSdkVersion:'36'`, no `android:debuggable="true"`, and only the permissions listed in [PRIVACY.md](PRIVACY.md) plus any added by libraries (review those).

## Optional build properties

`-Pfinances.privacyPolicyUrl=...` adds an "Open the online version" button to the in-app policy, and `-Pfinances.privacyContact=...` fills in the contact line. Set them only with a verified public URL and a real contact address.

## CI

`.github/workflows/android.yml` runs unit tests, lint and a debug build on pushes and pull requests to `main`. `.github/workflows/release.yml` is manual (`workflow_dispatch`) and needs the secrets `FINANCES_KEYSTORE_BASE64`, `FINANCES_KEYSTORE_PASSWORD`, `FINANCES_KEY_ALIAS` and `FINANCES_KEY_PASSWORD`; it fails if the keystore secret is missing, runs the 16 KB check and uploads the AAB and APK as workflow artifacts.
