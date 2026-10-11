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

`-Pfinances.privacyPolicyUrl=...` adds an "Open the online version" button to the in-app policy, and `-Pfinances.privacyContact=...` fills in the contact line (set to the developer contact in `gradle.properties`). Set the URL only once the policy is publicly hosted.

## Publishing a release (step by step)

The release workflow `.github/workflows/release.yml` runs when you push a tag that starts with `v` and contains no hyphen (a tag such as `v2.1.0-beta.1` is treated as a hand-published beta pre-release and does not start it). It builds a signed release APK and AAB, verifies the APK signature, runs the 16 KB check, and attaches `Finances-vX.Y.Z.apk` and its `.sha256` file to a GitHub Release. It never signs with a debug key: without the four secrets below it stops with an error.

### 1. Create the upload keystore (once, on your own computer)

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong passwords and store the `.jks` file and both passwords in a password manager plus an offline backup. **If you lose this key you can no longer publish updates that Android accepts over existing installs.** Never commit it (it is git-ignored) and never paste it or its passwords into a chat, an issue or a log.

### 2. Turn the keystore into a base64 secret

```bash
base64 -w0 upload-keystore.jks > keystore.b64      # macOS: base64 -i upload-keystore.jks -o keystore.b64
```

Open `keystore.b64` in a text editor and copy its whole content (one long line). Delete `keystore.b64` afterwards.

### 3. Add the GitHub secrets

Repository **Settings > Secrets and variables > Actions > New repository secret**. Create exactly these four:

| Secret | Value |
| --- | --- |
| `FINANCES_KEYSTORE_BASE64` | the content of `keystore.b64` |
| `FINANCES_KEYSTORE_PASSWORD` | the keystore password |
| `FINANCES_KEY_ALIAS` | `upload` (the alias from step 1) |
| `FINANCES_KEY_PASSWORD` | the key password |

(With the GitHub CLI you can instead run `gh secret set FINANCES_KEYSTORE_BASE64 < keystore.b64` and `gh secret set` for the others, which prompts for the value.)

### 4. Set the version and tag it

1. In `app/build.gradle.kts` increase `versionCode` and set `versionName`, for example `"2.1.0"`. Commit and push to `main`; wait for the CI to pass.
2. Create and push a tag that matches `versionName` exactly, with a `v` prefix:
   ```bash
   git tag v2.1.0
   git push origin v2.1.0
   ```
   The workflow fails early if the tag and `versionName` differ.
3. Watch **Actions > Android release build**. When it is green, the APK is on the repository's **Releases** page, and `releases/latest` points to it.

"Run workflow" in the Actions tab does the same build without publishing a Release; the files are then only available as workflow artifacts.

### 5. After the first release

Check the signature on a downloaded copy (`apksigner verify --print-certs Finances-v2.1.0.apk`), install it on a phone, and note the certificate SHA-256 somewhere safe so users and you can recognise genuine releases. The release build (R8, lint of the release variant, 16 KB check) has not been run before the first tagged build, so expect to fix issues the first time.

## CI

`.github/workflows/android.yml` runs unit tests, lint and a debug build on pushes and pull requests to `main` and on manual runs; the debug APK is uploaded as a workflow artifact for 14 days (testing only, not a release). `.github/workflows/release.yml` is described above.
