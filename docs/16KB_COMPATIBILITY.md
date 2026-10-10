# 16 KB page-size compatibility

Google Play requires apps targeting Android 15+ that ship native code to support devices with 16 KB memory pages. This app has no native code of its own; native libraries come from dependencies.

## Native libraries expected

| Library (typical name) | Comes from | Status |
| --- | --- | --- |
| `libimage_processing_util_jni.so` | CameraX (`camera-core`) | CameraX 1.3.x was not built for 16 KB pages; 1.4.0 added support. The app now uses **1.4.2**. |
| ML Kit text recognition native code (for example `libmlkit_google_ocr_pipeline.so`) | `com.google.mlkit:text-recognition` | ML Kit's release notes record a 16 KB page-size update for its Android SDKs on 7 Aug 2024, the date of `text-recognition` **16.0.1**, which the app now uses (was 16.0.0). |
| `libdatastore_shared_counter.so` | only in DataStore 1.1+ | not expected: the app uses DataStore 1.0.0 |

Exact file names must be confirmed from a release build.

## How to verify

1. Build the release APK and AAB (see [RELEASE.md](RELEASE.md)).
2. `scripts/check-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk` checks that every `.so` has ELF LOAD segments aligned to at least 16 KB and, if build-tools 35+ is installed, runs `zipalign -c -P 16 -v 4`.
3. In Android Studio, Build > Analyze APK shows a warning for misaligned libraries.
4. Run the app on a 16 KB emulator image (Android 15+ "16 KB page size" system image): start, scan a receipt, open the camera.
5. After upload, Play Console's App bundle explorer reports 16 KB compliance.

The script itself was tested against an ELF built with `-z max-page-size=16384` (passes) and with 4 KB alignment (fails).

## Result

**Not verified yet.** No APK could be built in the environment used for this round of work (no access to Google's Maven repository). The dependency versions were chosen based on their release notes, but compliance must be confirmed with the steps above before release.

## 32-bit libraries

The check script fails on any 64-bit library (`arm64-v8a`, `x86_64`) whose LOAD segments are aligned to less than 16 KB. For the 32-bit ABIs (`armeabi-v7a`, `x86`) it only prints `WARN_32BIT`, because 16 KB page-size devices are 64-bit. The CI release check showed that the ML Kit text-recognition library (`libmlkit_google_ocr_pipeline.so`) is 4 KB aligned in its 32-bit variants and 16 KB aligned in its 64-bit variants. Confirm the current rule in Google's "Support 16 KB page sizes" guidance and Play Console before release.
