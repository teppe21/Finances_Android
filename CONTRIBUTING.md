# Contributing

Thanks for your interest in Finances. This is a small personal project, so please keep changes focused.

## Build and test

Requirements: JDK 17 and an Android SDK with platform 36 (see [DEVELOPMENT.md](DEVELOPMENT.md)).

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lint                # Android lint
./gradlew assembleDebug       # debug APK
```

The same three tasks run in CI on every push and pull request. A pull request should pass all of them.

## Issues

- Search existing issues first.
- For a bug, include the app version (Settings > About), Android version and phone model, what you did, and what you expected.
- **Never paste real bank notification text, card numbers, account numbers or balances.** Replace them with made-up values.
- For a security problem, do not open a public issue; see [SECURITY.md](SECURITY.md).

## Pull requests

- Keep each pull request to one topic and describe what changed and why.
- Add or update tests for behaviour changes (parsers, money handling, migrations). Do not delete or weaken a test to make it pass.
- Test fixtures must be synthetic. Do not commit real personal or financial data, API keys, keystores or passwords.
- Adding a supported notification source: follow "Adding a supported notification source" in [DEVELOPMENT.md](DEVELOPMENT.md).
- Changing a Room entity: bump the database version, add a migration and commit the exported schema.

By contributing you agree that your contribution is released under the [MIT License](LICENSE).
