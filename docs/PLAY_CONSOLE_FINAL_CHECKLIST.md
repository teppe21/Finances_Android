# Play Console final checklist

Status legend: **PASS** (done in the repository and verified), **PASS WITH MANUAL ACTION** (prepared in the repository, a person must finish it), **BLOCKED** (cannot be completed until something external is provided or run).

## Repository and build

| Item | Status | Notes |
| --- | --- | --- |
| Standalone project, own Gradle root | PASS | |
| `compileSdk`/`targetSdk` 36 | PASS | set in `app/build.gradle.kts` |
| AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 | BLOCKED | versions chosen to be compatible, but no Android build has been run yet |
| Unit tests | PASS WITH MANUAL ACTION | 83/83 pass in a JVM harness; run `./gradlew testDebugUnitTest` once Google Maven is reachable |
| Lint | BLOCKED | not run |
| Debug / release APK, AAB | BLOCKED | not built |
| Release signing without debug fallback | PASS WITH MANUAL ACTION | build logic in place; upload keystore must be created and supplied |
| R8 / resource shrinking | PASS WITH MANUAL ACTION | enabled; verify the release build runs (Room, ML Kit, CameraX keep rules) |
| `debuggable = false` in release | PASS WITH MANUAL ACTION | set explicitly; confirm with `aapt2` |
| Permissions minimized | PASS WITH MANUAL ACTION | confirm the merged manifest |
| Backup excludes financial data | PASS | `allowBackup=false` plus exclusion rules |
| 16 KB page size | BLOCKED | dependencies updated; run `scripts/check-16kb-alignment.sh` on the release APK |
| Instrumented migration test | BLOCKED | needs a device or emulator |
| Real-device test plan | BLOCKED | see REAL_DEVICE_TEST_PLAN.md |

## Play Console

| Item | Status | Notes |
| --- | --- | --- |
| App created with package `com.teppe21.finances` | PASS WITH MANUAL ACTION | |
| Play App Signing and upload key | PASS WITH MANUAL ACTION | |
| Privacy policy URL | BLOCKED | needs hosting and a real developer name/contact in `docs/privacy-policy.html` |
| Data safety form | PASS WITH MANUAL ACTION | draft in PLAY_CONSOLE_COMPLIANCE.md |
| Financial features declaration | PASS WITH MANUAL ACTION | reasoning in PLAY_FINANCIAL_FEATURES_DECLARATION.md |
| Content rating questionnaire | PASS WITH MANUAL ACTION | |
| Target audience (18+) | PASS WITH MANUAL ACTION | |
| App access instructions for reviewers | PASS WITH MANUAL ACTION | text in PLAY_REVIEW_INSTRUCTIONS.md |
| Store listing text | PASS WITH MANUAL ACTION | draft in PLAY_STORE_LISTING.md |
| Screenshots and feature graphic | BLOCKED | must be captured from a real build; none are included |
| Closed testing track (personal accounts created after Nov 2023 need a 12-tester, 14-day closed test before production) | PASS WITH MANUAL ACTION | check the current rule for your account |

## Hosting the privacy policy on GitHub Pages

1. Replace the placeholders marked `[TO BE COMPLETED]` in `docs/privacy-policy.html` with the developer name and a contact email.
2. In the GitHub repository: Settings > Pages > Build and deployment > Source: "Deploy from a branch", branch `main`, folder `/docs`. Save.
3. Wait for the deployment, then open `https://<user>.github.io/<repository>/privacy-policy.html` in a browser and in a private window.
4. Use that URL in Play Console and build with `-Pfinances.privacyPolicyUrl=<url>` so the app links to it.

The URL has not been published or verified as part of this work.
