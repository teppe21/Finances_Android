# Handoff: Finances (Android) – categorization, typography/layout, motion

Written at the end of a Claude Code session that stopped on a usage limit. Everything below was
established from the repository and from the session itself; items marked *not verified* were
not checked.

## 1. Project overview

"Finances" is a native Android personal finance app: Kotlin, Jetpack Compose (BOM 2024.02.01,
Material 3), Room 2.6.1 (now schema 6), DataStore, single Gradle module `:app`, minSdk 26,
compile/target 36, offline-first, no backend, no analytics. Dependencies are wired by hand in
`FinancesApp`. Domain logic (`domain/`, `core/`, `native/`) is plain Kotlin and JVM-tested.
Docs: `README.md`, `docs/ARCHITECTURE.md`, `docs/DATA_MODEL.md`, `docs/IMPORT_FORMATS.md`,
`docs/MOTION_DESIGN.md`, `docs/TESTING.md`, `docs/REAL_DEVICE_TEST_PLAN.md`.

## 2. Original objective and requirements (from the user)

Three phases, in order, each with its own commits; do not start a phase before the previous one
builds and passes tests.

Ground rules: keep architecture small (no new layers/DI); no new network calls, analytics or
telemetry; every new dependency must be justified (purpose, why platform can't, license,
maintenance); **English and Hungarian must stay consistent** (every user-facing string in both
`values/strings.xml` and `values-hu/strings.xml`); never invent measurements or claim fixes
without evidence; never commit real personal data (synthetic fixtures only); after each phase run
`./gradlew testDebugUnitTest lint assembleDebug` (+ instrumented tests if an emulator is
available); add/update tests for behaviour changes; update docs (IMPORT_FORMATS, DATA_MODEL,
MOTION_DESIGN, TESTING, README feature text).

- **Phase 1 – categorization after CSV import.** Kinds EXPENSE / INCOME / TRANSFER (own accounts,
  exchanges, balance migrations, top-ups, ATM) / PERSONAL_TRANSFER; only EXPENSE/INCOME feed
  spending, top merchants, budgets, averages, trend; transfers shown separately. Pattern-based
  recognition (Exchanged to/from CCY, Átváltás, Balance migration, Transfer to/from, Top-up,
  Saját számlák közötti, Átutalás, Revolut type column). Accent/case normalization keeping
  "ber must not match Uber". Import summary, uncategorized review grouped by merchant,
  "create rule from this", "re-apply rules" never overwriting manual categories.
  Before/after uncategorized rate on synthetic data. **DONE (see §4).**
- **Phase 2 – typography/layout.** Audit all screens; one design system in existing theme files
  (type scale, numeric amount style with `tnum`, 8 dp spacing, radii, elevations, contrast light
  and dark); stable list rows (amount right-aligned, never truncated; merchant ellipsized;
  secondary info quieter). Six specific Analytics bugs reported by the user (hint on own line /
  donut butt caps + gaps + min sweep + stable order / top-merchants missing amount and gap /
  hard cut under header / monospace amounts with wide gap before "Ft" / uneven row spacing).
  Verify at 360 dp, font scale 1.0/1.3/1.5, English + Hungarian, light + dark, very long
  merchant, very large amount, 1-char merchant. Add Compose previews or screenshot-style tests;
  check TalkBack content descriptions. **PARTLY DONE.**
- **Phase 3 – iOS-like motion + subtle "liquid glass".** Shared spring specs in one place;
  shared element / container transforms (upgrade Compose BOM carefully if needed and say what
  changed); predictive back with follow-the-finger; `Modifier.animateItem` list animations;
  press feedback; animated numbers/charts/month slide; draggable interruptible sheets/dialogs;
  respect animator duration scale. Translucent chrome (tab bar, top bars, sheets) with real blur
  only on API 31+ and a tinted fallback; evaluate a maintained backdrop-blur library vs
  `RenderEffect`, pick the simpler/safer one and justify; limit blur. Performance (stable params,
  `derivedStateOf`, `graphicsLayer`), baseline profile if cheap; list physical-device
  measurements in `docs/REAL_DEVICE_TEST_PLAN.md`. **PARTLY DONE in Stage 3 (see §16).**
- Final report per phase: root causes, changes, what was/wasn't verified, trade-offs, disagreements.

User decisions already made:
- **Full Hungarian translation** (the repo was English-only with `resourceConfigurations = "en"`);
  done in commit `c1e4f42`.
- Transfers stay visible on their own Analytics card but never count as spending (from the user's
  evidence section).

## 3. Current repository state

- Branch: `claude/categorization-layout-motion` (created from `claude/app-icon`, whose tip
  `1b93a5a` is an earlier, unmerged app-icon commit). Not pushed. No PR.
- Working tree: **not clean**. HEAD is still `dbd31b5`; the Stage 2, Stage 3 and Stage 3.1 work is all uncommitted (24 modified and 10 untracked files, listed in §17.6; nothing staged, committed or pushed). Everything below this line in §3 to §13 describes the state at the end of the first session and is kept for history; the current state is §17.
- Commits made in this session (oldest first):
  - `36009d5` Recognize transfers and fix CSV categorization (Phase 1 code + tests)
  - `bac4710` Document categorization, transfer kinds and schema 6 (Phase 1 docs)
  - `c1e4f42` Translate the app into Hungarian and format dates and amounts per language
  - `dbd31b5` Add layout tokens and shared rows; fix the analytics layout bugs
- Phase 1 code is one commit because the pieces did not compile separately.

## 4. Work completed

### Phase 1 (verified)
- `domain/model/BuiltIns.kt`: built-in categories (added `personal_transfers`, `cash`, `taxes`,
  `fees`, `interest`, `electronics`), schema-5 rules, `schema6Updates`, `schema6Rules` (ids
  `b6_<category>_<slug>`), `kindFor(categoryId, sign)`, CSV category-label mapping.
- `domain/usecase/CategorizeTransactionUseCase.kt`: `classify()` with priorities (user 50, own
  transfer 30, fees 22, cash 20, bank category 15, merchant 10, generic transfer 5, weak income
  4); short keywords (≤3 chars) whole-word; joined multi-word match; `word`/`exact`/`regex`;
  income-only rules never on debits; card-type bank rows never transfers by built-in keyword.
- `domain/usecase/IngestTransactionUseCase.kt`: category decides kind; merchant null for
  transfers; own-transfer leg linking (`transferId`, ±3 days).
- `domain/usecase/Recategorization.kt`: `ReapplyRulesUseCase` (never touches
  `categorySetByUser`, never downgrades to "other"), `withUserCategory`, `UncategorizedGroups`,
  `Recategorizer` (wired in `FinancesApp`).
- `native/csv/CsvParser.kt` + `CsvImport.kt`: role-based header detection (fixed `Értéknap` read as
  amount), type/state/category columns, all description columns joined with " · ", reverted/
  declined rows skipped (`NOT_COMPLETED`), legacy `fingerprintText` so re-imports don't duplicate;
  `ImportSummary`.
- `core/utils/TextNormalizer.kt`: apostrophes dropped; card-noise stripping in `cleanMerchantName`.
- Room schema 6 (`AppDatabase.MIGRATION_5_6`, `app/schemas/.../6.json`), `categorySetByUser` column.
- UI: import summary (`feature/importcsv`), `feature/categories/UncategorizedScreen.kt` +
  ViewModel + `CategorizeDialog.kt`, "Change" category in transaction detail, More entry, transfers
  card in Analytics.
- Evidence: `CategorizationHarnessTest` (synthetic fixtures in `app/src/test/.../support/SyntheticStatements.kt`):
  before 53.5 % uncategorized / 21 of 21 transfers counted as income-expense / 19 OTP rows
  skipped; after 4.5 % / 0 / 0. Holdout set (not tuned): 38.9 % uncategorized, 1 wrong kind.
  Numbers documented in `docs/IMPORT_FORMATS.md`. Emulator end-to-end import confirmed.

### Hungarian translation (verified by test + emulator screenshot)
- `values-hu/strings.xml` complete; `TranslationParityTest` enforces keys/placeholders/plurals.
- `core/utils/DateFormatter.kt` (`DisplayLocale`), `CurrencyFormatter` (NBSP, HU grouping/decimal
  comma, `formatPercent`), set in `MainActivity.onCreate`; `res/xml/locales_config.xml`; hu in
  `resourceConfigurations`.
- `feature/categories/CategoryLabels.kt`: `categoryLabel`, `accountLabel`, `accountTypeLabel`,
  `sourceLabel`; `Category.hasDefaultName` / `Account.DEFAULT_NAMES` (renames of built-ins now
  respected; previously ignored).
- ViewModels expose resource ids / typed results (Settings, Receipt, Notification samples, Stocks).
- The Hungarian privacy-policy text is a machine/agent translation: **needs native review**.

### Phase 2 so far (commit `dbd31b5`; unit tests + lint pass; partly checked on emulator)
- `core/theme/Dimens.kt` (Spacing, Radii, Elevation, Sizes); amount text styles in `core/theme/Type.kt`
  (`amountHero`, `amountLarge`, `amount`, `amountSmall`).
- `core/ui/Rows.kt`: `AmountRowLayout` (amount measured first, never truncated; moves under the
  name if the name would be < 96 dp), `AmountText`, `ListRow`, `ValueRow`. `SectionCard` has
  `subtitle`. `core/ui/Edges.kt`: `topScrollEdge`.
- Analytics (`feature/analytics/AnalyticsScreen.kt`, `DonutGeometry.kt`): summary card with
  income/expense/net, butt-cap donut with gaps + min sweep + stable order, hint on its own line,
  shared rows, centre total steps down in size, "<0.1%".
- `MainActivity`: pinned top-bar scroll behaviour + content top fade; one-line titles.
- `core/ui/Components.kt` `TransactionRow` re-laid out (merchant + amount, category · date below).
- Dashboard tiles stack when an amount would not fit; zero totals no longer "+0 / -0".

## 5. Work in progress at session termination

The session was verifying Phase 2 on the emulator (360 dp, Hungarian, light). Last checked
screens: Dashboard, Import summary, Transactions, Analytics. After the last screenshots two fixes
(donut centre auto-size, "<0.1%") were made, unit tests + lint + assemble passed, and the work was
committed as `dbd31b5`. The emulator density/locale were reset; the emulator process then exited.
The donut-centre fix itself was **not re-checked on screen**.

## 6. Outstanding tasks (historical, from the first session; items 1 to 7 were worked on in Stages 1 to 3 and what is still open is in §15.7, §16.8 and §17.7)

**P1 – finish Phase 2**
1. Run instrumented tests again (last full run: 36/36 passed *before* `c1e4f42` and `dbd31b5`).
   UI tests use English strings; the import screen title changed to "Import CSV statement".
2. Verify at font scale 1.3 and 1.5, English and Hungarian, light and dark, 360 dp; screens:
   transactions, import, analytics, budgets, stocks, settings, review queue, uncategorized,
   dialogs (CategorizeDialog, add transaction, budget dialog – its 5 currency chips in one `Row`
   may overflow at 360 dp), bottom sheets (transaction detail – `DetailRow` in TransactionsScreen
   is still a plain SpaceBetween row).
3. Apply the shared rows/tokens to remaining screens not yet converted (Budgets, Accounts,
   Stocks, Settings, Review, Dashboard category card, transaction detail sheet); check remaining
   ad-hoc `RoundedCornerShape(..)` / dp values.
4. Add Compose previews or screenshot-style tests for key screens (e.g. Compose UI tests asserting
   the amount node is fully displayed in `ListRow`/`AmountRowLayout` at small widths and font scale 1.5).
5. TalkBack content descriptions for touched components.
6. Unit tests for `donutSegments` (`DonutGeometry.kt`) – none written yet.
7. Docs for Phase 2: `docs/MOTION_DESIGN.md` (typography/colour section, tokens, rows),
   `docs/TESTING.md` (counts are now 198 unit tests; instrumented count unchanged at 36 until re-run),
   README.

**P1 – Phase 3** (only after Phase 2 builds and passes): see §2. Proposed direction (not yet
implemented or approved): blur only tab bar + top bar on API 31+, tinted semi-opaque fallback.

**P2** – final report to the user covering all phases.

## 7. Known problems and root-cause analysis

- Confirmed (fixed): top-merchant amount missing + tall row – unweighted name in a SpaceBetween
  `Row` took full width, amount got 0 width and wrapped one char per line.
- Confirmed (fixed): donut round caps overlapped neighbours; hint shown only after selection.
- Confirmed (fixed): `Értéknap` header matched amount keyword `ertek`.
- Open: holdout data shows keyword lists miss unknown merchants (38.9 %); expected, handled by rules UI.
- Open/unverified: donut centre auto-size on device; behaviour at font scale 1.3/1.5; dark theme.
- Not a bug but note: `"Possible duplicate"` / `"Demo data"` are English *data markers* stored in
  `notes`; they are intentionally not translated.

## 8. Important files

- `app/src/main/java/com/teppe21/finances/core/theme/{Dimens,Type,Theme,Color}.kt` – design tokens.
- `.../core/ui/{Rows,Components,Edges,Motion,Gestures,TabBar}.kt` – shared UI; Motion/TabBar for Phase 3.
- `.../feature/analytics/{AnalyticsScreen,DonutGeometry,AnalyticsViewModel}.kt`
- `.../MainActivity.kt` – scaffold, top bar, nav transitions (Phase 3 entry point), lock screen.
- `.../domain/model/BuiltIns.kt`, `.../domain/usecase/{CategorizeTransactionUseCase,IngestTransactionUseCase,Recategorization}.kt`
- `.../native/csv/{CsvParser,CsvImport}.kt`
- `app/src/main/res/values{,-hu}/strings.xml`
- Tests: `app/src/test/.../{CategorizationTest,CategorizationHarnessTest,TranslationParityTest,MoneyParsingTest}.kt`,
  `app/src/androidTest/.../{MigrationTest,DatabaseTest,UiFlowTest}.kt`
- `app/build.gradle.kts` (Compose BOM 2024.02.01; Phase 3 may need an upgrade).

## 9. Validation status (historical: at commit `dbd31b5`; the current figures are in §17.4, and the "Not executed" list below is partly out of date)

Passed (2026-10-11, this machine):
- `testDebugUnitTest`: 198 tests, 0 failures (after `dbd31b5`).
- `lint`: 0 errors, 28 warnings (after `dbd31b5`).
- `assembleDebug`: passed.
- `connectedDebugAndroidTest`: 36 passed on API 35 emulator – **before** the translation and
  layout commits.
- Emulator: Phase 1 import flow; Hungarian UI; 360 dp HU light screenshots of Dashboard,
  Import summary, Transactions, Analytics.

Not executed: instrumented tests after `c1e4f42`/`dbd31b5`; font scale 1.3/1.5; dark theme;
TalkBack; anything on a physical device; frame-rate measurements.

**Update (Stage 1 audit, same day, see §14):** the instrumented suite *was* re-run against
`dbd31b5` and passed 36/36, and `testDebugUnitTest` was force-re-run (198/198). The "Not executed"
list above still applies to everything except the instrumented tests.

## 10. Constraints and guidance

- Build environment (Windows): JDK at `G:\tools\jdk-17.0.12+7`, SDK `G:\tools\android-sdk`,
  `ANDROID_USER_HOME=G:\tools\android-cache`, `GRADLE_USER_HOME=G:\tools\gradle-cache`. Gradle
  fails with "Unable to establish loopback connection" unless TEMP/TMP point to `G:\tools\tmp`
  and `JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=G:/tools/tmp -Djdk.net.unixdomain.tmpdir=G:/tools/tmp"`;
  it also needed to run outside the agent sandbox. Use `--offline`.
- Emulator: AVD `finances_api35` in `G:\tools\android-cache\avd`. In Git Bash set
  `MSYS_NO_PATHCONV=1` for adb paths. 360 dp = `adb shell wm density 480`; per-app Hungarian:
  `adb shell cmd locale set-app-locales com.teppe21.finances --locales hu-HU`. A differently signed
  build may need `adb uninstall com.teppe21.finances` first (emulator only).
- Every new string → both string files (`TranslationParityTest` will fail otherwise). Avoid ASCII
  apostrophes in XML (use `’`).
- Amounts: use `CurrencyFormatter` (never hardcode signs/units), `AmountText`/`ListRow`/`ValueRow`;
  never `FontFamily.Monospace`.
- Do not change built-in rule ids or remove shipped keywords (migration logic depends on ids).
- Keep CSV fingerprints on `fingerprintText` (re-import compatibility).
- Do not redo Phase 1 or the translation.

## 11. Exact continuation plan (historical; superseded by §17.7)

1. **Restore the environment and re-run instrumented tests.** Boot `finances_api35`, run
   `connectedDebugAndroidTest`; fix any failures caused by `c1e4f42`/`dbd31b5` (text changes).
   Done when all instrumented tests pass.
2. **Phase 2 verification pass.** Font scale 1.0/1.3/1.5 × EN/HU × light/dark at 360 dp on the
   listed screens with the stress data (long merchant, 987 654 321 Ft, merchant "X"). Fix overflow
   using the shared rows/tokens. Done when screenshots show no truncated amounts or collisions.
3. **Convert remaining screens to tokens/shared rows** (Budgets, Accounts, Stocks, Settings,
   Review, transaction detail sheet, dialogs). Verify by screenshots + build.
4. **Tests:** `donutSegments` unit tests; Compose tests for `AmountRowLayout` at narrow width /
   large font. Verify with `testDebugUnitTest` and `connectedDebugAndroidTest`.
5. **TalkBack descriptions** on touched components; verify with semantics assertions.
6. **Docs + commit Phase 2** (`MOTION_DESIGN.md`, `TESTING.md`, README). Run
   `testDebugUnitTest lint assembleDebug`.
7. **Phase 3** per §2; evaluate blur approach and BOM upgrade before coding; ask the user if a new
   dependency is proposed.
8. **Final report** per the user's format.

## 12. Instructions for the next AI agent

- Read this file, `README.md` and the docs in `docs/` before changing anything.
- Run `git status` / `git log` and review the four commits listed in §3.
- Preserve existing work; do not revert commits or rewrite completed phases.
- Verify the documented state (build, tests) instead of trusting it.
- Continue the original task; do not add unrelated features.
- Change incrementally and validate each step; commit per logical step.
- Update this handoff file at the end of your session.
- Ask the user when a significant decision (e.g. a new dependency, BOM upgrade risks) is unclear.

## 13. Immediate next action (superseded by §15.9)

~~Boot the emulator and re-run `connectedDebugAndroidTest`~~ — **done in the Stage 1 audit (§14): 36/36
passed on `dbd31b5`.** The immediate next action is now the one in §14.7 (Stage 2).

## 14. Stage 1 audit and test baseline (2026-10-11, read-only except this file)

Scope of that session: inspect, verify the handoff against the repository, establish the test
baseline. No application code, tests, dependencies or configuration were changed. `HANDOFF.md` is the
only file touched (it is still untracked).

### 14.1 Repository and Git state (verified)
- Branch `claude/categorization-layout-motion` (expected branch: yes). HEAD `dbd31b5`
  "Add layout tokens and shared rows; fix the analytics layout bugs". Not pushed, no PR (no
  `origin/claude/categorization-layout-motion` ref exists).
- `git status`: only `?? HANDOFF.md`. Unchanged before and after all builds and test runs
  (build output is git-ignored; `app/schemas` was not re-exported/modified).
- The four commits in §3 exist in the stated order on top of `1b93a5a`. Combined diff from
  `1b93a5a`: 78 files, +6004 / −1185.
- No `CLAUDE.md` or `AGENTS.md` anywhere in the repository (tracked or untracked). Project guidance
  lives in `README.md`, `DEVELOPMENT.md`, `CONTRIBUTING.md` and `docs/`.

### 14.2 Commands executed and results
Environment variables were exported inside each Bash call (see §14.5); all runs used `--offline`
and the repository's Gradle wrapper (8.14.3), from `G:\Finances_Android`.

| Command | Result |
| --- | --- |
| `./gradlew.bat testDebugUnitTest --offline` | first run: all 28 tasks UP-TO-DATE, i.e. nothing executed (cached result) |
| `./gradlew.bat cleanTestDebugUnitTest testDebugUnitTest --offline` | **executed**; BUILD SUCCESSFUL; XML reports (24 files, timestamp 18:21): **198 tests, 0 failures, 0 errors, 0 skipped** |
| `emulator -avd finances_api35 -no-snapshot-save -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect` | cold boot, completed in ~25 s; `adb devices` showed `emulator-5554`; killed afterwards with `adb emu kill` |
| `./gradlew.bat connectedDebugAndroidTest --offline` | **executed** (test sources recompiled; app + test APK packaging UP-TO-DATE, i.e. identical to the current sources); "Starting 36 tests … Finished 36 tests on finances_api35(AVD) - 15"; BUILD SUCCESSFUL in 1m 9s; result XML: **tests="36" failures="0" errors="0" skipped="0"** |
| `./gradlew.bat lint assembleDebug --offline` and `lintDebug --rerun` | BUILD SUCCESSFUL, but Gradle did **not** rewrite the lint report (file timestamp still 15:47 from the previous session): the "0 errors, 28 warnings" figure is the earlier report, **not independently re-confirmed**. `assembleDebug` was UP-TO-DATE (APK matches current sources). |

Instrumented per-file `@Test` counts: `DatabaseTest` 15, `MigrationTest` 3, `UiFlowTest` 18
(`ComponentUiTest`, `ReviewFlowUiTest`, `NavigationUiTest`, `UncategorizedFlowUiTest`) = 36, matching
the run. Unit `@Test` count in `app/src/test` = 198, matching the run.
**All 36 previously passing instrumented tests are now verified on `dbd31b5`.**

### 14.3 Requirements check (static review + test results; nothing rewritten)
- **Hungarian:** `values/strings.xml` and `values-hu/strings.xml` both have 541 `<string>` and 10
  `<plurals>`; `resourceConfigurations += listOf("en","hu")`; `locales_config.xml` and manifest
  `localeConfig` present; `TranslationParityTest` passes. On-screen Hungarian was *not* re-checked
  in this session. The Hungarian privacy-policy text still needs native review (unchanged).
- **Transfers separate / excluded from totals:** `CalculateFinancialStatsUseCase` counts only
  income-like and `EXPENSE` towards totals, trends and category breakdown; `TRANSFER` and
  `PERSONAL_TRANSFER` go to their own fields. `AnalyticsViewModel.kt:138` (top merchants) and
  `BudgetsScreen.kt:99` filter on `EXPENSE` only. `Components.kt` renders transfer rows with a
  separate colour/label/icon. Covered by `CategorizationTest`, `CategorizationHarnessTest`,
  `CurrencyAndStatsTest` (all passing). No discrepancy found.
- **Categorization improvements:** `BuiltIns`, `CategorizeTransactionUseCase`,
  `IngestTransactionUseCase`, `Recategorization`, schema 6 (`MIGRATION_5_6`, `6.json`) are present
  and tested (`MigrationTest`, `UncategorizedFlowUiTest` pass).
- **Local-first:** no analytics/ads/Firebase/crash-reporting dependency in `app/build.gradle.kts`.
  Manifest requests INTERNET, CAMERA, USE_BIOMETRIC; merged manifest adds `ACCESS_NETWORK_STATE`
  and `USE_FINGERPRINT` (from libraries). Outbound calls found in code: Frankfurter and
  open.er-api.com (currency codes only) and Finnhub (only with a user-supplied key, header auth).
  `allowBackup=false`. Matches README/docs. *Unverified:* whether the bundled ML Kit library or
  its transitive dependencies perform any network/telemetry activity at runtime (not inspected).
- **CSV import, notification detection, receipt scanning, keyword rules, multi-currency,
  analytics:** code present and covered by passing suites (`CsvAndReceipt`, `CsvBankHints`,
  `Notification*`, `Receipt*`, `CurrencyAndStats`, `CategorizationTest`). Not exercised on-device
  in this session.

### 14.4 Discrepancies between the handoff/docs and the repository
1. **Resolved:** §9/§13 said instrumented tests had not run since `c1e4f42`/`dbd31b5`; they now
   have (36/36).
2. §10 says "never `FontFamily.Monospace`", but `feature/subscreens/SubScreens.kt:100` (recurring
   rule estimated amount) still uses it. This is part of the unfinished "convert remaining
   screens" task (§6.3), not a regression.
3. `RoundedCornerShape(` still appears 48 times in `app/src/main/java` (ad-hoc shapes, §6.3).
4. `docs/TESTING.md` still says **196** unit tests (header and "What has been run"); actual is 198
   (already listed as a Phase 2 docs task in §6.7). Its instrumented count (36) is correct.
5. §6.1 says UI tests are affected by the changed import screen title. No instrumented test
   references the import screen, and none broke. The instrumented suite is English-only and runs in
   the emulator's default locale; there are **no instrumented tests for Hungarian or font scale**.
6. §10 says Gradle "needed to run outside the agent sandbox". In this session Gradle, adb and the
   emulator ran through the normal Bash tool with no sandbox override, once the environment
   variables in §14.5 were exported. Not a contradiction of the earlier session, but the
   requirement may be tool-/permission-mode dependent.
7. §9 lint figures (0 errors / 28 warnings) could not be re-verified (see §14.2).
8. §5 said the emulator had exited; confirmed: no emulator was running at the start of this session.

### 14.5 Environment (verified present)
- JDK `G:\tools\jdk-17.0.12+7`; SDK `G:\tools\android-sdk` (platforms 34/35/36, build-tools
  34.0.0/35.0.0/35.0.1/36.0.0, platform-tools, emulator, system image
  `android-35/google_apis/x86_64`); AVD `finances_api35` in `G:\tools\android-cache\avd`;
  Gradle caches in `G:\tools\gradle-cache` (wrapper dist 8.14.3 present, so `--offline` works);
  `G:\tools\tmp` exists. WHPX acceleration reported usable by `emulator -accel-check`.
- The shell's default TEMP/TMP is the 8.3 path `C:\Users\LAMFAL~1\...`, and `java`/`adb` are not on
  PATH. Per Bash call I exported: `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_USER_HOME=G:\tools\android-cache`,
  `GRADLE_USER_HOME`, `TEMP`/`TMP=G:\tools\tmp`,
  `JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=G:/tools/tmp -Djdk.net.unixdomain.tmpdir=G:/tools/tmp"` and
  prepended the JDK and platform-tools `bin` directories to `PATH`; then ran `./gradlew.bat …`.
  The emulator additionally needed `ANDROID_AVD_HOME=G:\tools\android-cache\avd` and
  `ANDROID_SDK_ROOT`.
- No `local.properties` / `keystore.properties` exist (not needed for debug builds).
- Side effects: `connectedDebugAndroidTest` uninstalls the debug app from the emulator (documented);
  Gradle wrote only to git-ignored build directories. The emulator was started with
  `-no-snapshot-save`, so its saved state was not changed. Emulator log: `G:\tools\tmp\emulator-stage1.log`.

### 14.6 Confirmed regressions versus unverified concerns
- **Confirmed regressions: none found.** Unit 198/198 and instrumented 36/36 pass on `dbd31b5`.
- **Unverified (not tested):** donut centre auto-size on screen; font scale 1.3/1.5; dark theme at
  360 dp; Hungarian layout beyond the screens screenshotted last session; TalkBack; physical device;
  lint figures; ML Kit runtime network behaviour; budget dialog's 5 currency chips at 360 dp.
- **Useful tests not yet present:** `donutSegments` unit tests (§6.6); Compose tests asserting
  `AmountRowLayout`/`ListRow` show the full amount at narrow width and font scale 1.5; at least one
  instrumented UI test under the `hu` locale (guards string/layout regressions the English-only
  suite cannot see); a test that a transfer row never changes Analytics spending totals end-to-end.

### 14.7 Recommended next action for Stage 2
Start Phase 2 verification (§11 step 2) on the already-green baseline: boot the emulator with the
§14.5 environment, set 360 dp (`adb shell wm density 480`) and run the screenshot pass for
font scale 1.0/1.3/1.5 × English/Hungarian × light/dark on Dashboard, Transactions, Analytics,
Budgets, Import, Uncategorized and the transaction-detail sheet using the stress data (long
merchant, 987 654 321 Ft, merchant "X"), recording findings *before* fixing anything. Then
convert the remaining screens (including `SubScreens.kt:100` Monospace and the ad-hoc
`RoundedCornerShape` uses) and add the missing tests from §14.6. Phase 3 stays blocked until Phase 2
is finished. Before committing Phase 2, commit or deliberately leave this file as an untracked
handoff, and update `docs/TESTING.md` (198 unit tests).

## 15. Stage 2 report: typography, layout and accessibility (2026-10-11)

Scope: Phase 2 only. Nothing was committed, pushed, reset or amended; **all Stage 2 work is uncommitted in the working tree** on `claude/categorization-layout-motion` (HEAD is still `dbd31b5`), for the user's review. No Phase 3 work (no motion, no glass). No dependency or build-configuration change. No categorization rule, currency conversion, import, notification or receipt code was touched.

### 15.1 Start state (verified before editing)
`git status` showed only `?? HANDOFF.md`; branch and HEAD as in §14.1; `app/schemas` is unchanged by this session. The §14 baseline (unit 198, instrumented 36) was taken as the reference.

### 15.2 Method
Emulator `finances_api35` at 360 dp (`wm density 480`). A synthetic stress data set was inserted straight into the emulator's debug database (`sqlite3` through `run-as`; never committed; the instrumented run uninstalls the app and wipes it): a 90-character merchant, the merchant "X" and "Q", a 987,654,321 Ft purchase (separate rows), a 14.99 EUR row, own-account exchanges (30,000 Ft out and in), an ATM withdrawal, a payment to a person, income. The expected spending of the base set was worked out by hand (217,370 Ft + the EUR row at the cached rate = 222,843 Ft) and the app showed exactly that; Analytics "Net" was +297,157 (520,000 − 222,843). The 350,000 Ft of own transfers/cash and the 15,000 Ft sent to a person appeared only on the Transfers card. The helper scripts live in the session scratchpad only (not in the repository).

### 15.3 Defects found (font scale 1.5 unless noted) and fixed
| # | Defect (screen) | Cause | Fix |
| --- | --- | --- | --- |
| 1 | An account balance wrapped one character per line (Accounts, "OTP Current Account −62,370 Ft") | `AccountCard` still used a plain `SpaceBetween` row | card rebuilt on `AmountRowLayout`/`AmountText`, tokens, merged semantics |
| 2 | Currency chips: the 4th and 5th chip collapsed to a sliver or stacked letters (Budget dialog, Settings, Add account, Add transaction) | five chips in a plain `Row` | new `ChipFlow`/`CurrencyChips` (`FlowRow`) in `core/ui/Chips.kt` |
| 3 | Tab bar label "Transactions" cut to "Transactic" | fixed-width item, `overflow = Clip` | new `ShrinkToFitText` (`core/ui/FitText.kt`): shrinks to a floor of 11 sp at scale 1.0, then ellipsis |
| 4 | Legend rows lost their share ("1 transaction · …"); "Net (income minus expens…)" cut | subtitle `maxLines = 1`; `ValueRow` label 2 lines | subtitle 2 lines, `ValueRow` label 3, `ListRow` title 3 lines at scale ≥ 1.3 |
| 5 | Dates cut in transaction rows ("10 Oct 2…"); long names cut to 7 characters | one-line details, one-line name | details 2 lines; name 2 lines at scale ≥ 1.3 |
| 6 | Dashboard shortcut labels silently cut to their first word ("Add transaction" → "Add", "Tranzakció hozzáadása" → "Tranzakci"); also seen at 1.3 in English | `maxLines = 1`, clip | 2 lines, equal-height tiles, label scale capped at 1.3 |
| 7 | Dashboard category rows: name clipped to one line, share and amount in a plain row | ad-hoc row | `AmountRowLayout`, 2-line name, merged semantics |
| 8 | Transaction detail sheet: a long merchant could push the close button away; the amount could wrap; the sheet was not scrollable; `DetailRow` values collided | unweighted title, plain rows | title weighted (3 lines), amount on one line via `ShrinkToFitText`, `DetailRow` weighted and right-aligned, sheet scrolls, close button labelled |
| 9 | Filters at the top of Transactions took ~36 % of the screen at 1.5 | pinned header above the list | header moved into the `LazyColumn` (scrolls away). Trade-off: the empty/non-empty cross-fade of the list area was dropped (the search field must keep focus while typing) |
| 10 | Multi-field dialogs were not scrollable | plain `Column` | `DialogColumn` (Budget, Add account, Add transaction, Categories ×3, Settings PIN/lock/manual rates) |
| 11 | Empty-state cards: centred title, left-aligned body | `EmptyState` | body centred; Recurring now uses `EmptyState` |
| 12 | Recurring payment amount in `FontFamily.Monospace` (the §14.4 item 2) | not converted | `ListRow` |
| 13 | Donut centre: label, total and share did not fit the hole at 1.5 with a selected category | fixed hole, scaling text | label 1 line, total via `ShrinkToFitText`, text scale capped at 1.3 inside the hole; the legend carries everything at full scale |
| 14 | Donut had no description for screen readers | Canvas without semantics | spoken description (total and every category with its share), new string `analytics_donut_description` (EN + HU) |
| 15 | Income green `#2E7D4F` was 4.37:1 on the card surface (< 4.5:1) | palette | `#2B7549` (4.85:1). Found by the new `ThemeContrastTest` |
| 16 | Close / clear-search icons unlabelled | `contentDescription = null` | new strings `close`, `clear_search` (EN + HU) |
| 17 | 20 ad-hoc `RoundedCornerShape(20/12/8 dp)` | not converted | replaced by `Radii.card/control/small` (same values). Other sizes (18, 24, 30, 22, 4, 2, 3 dp) were left |
| 18 | At 1.3 a long word in a legend row broke mid-word ("Uncategoriz/ed") next to a 987,667,166 Ft amount | the 96 dp minimum width for a name did not grow with the font | `AmountRowLayout` scales that minimum with the font scale (the amount then moves under the name) |

### 15.4 Files changed (all uncommitted)
- New: `core/ui/Chips.kt`, `core/ui/FitText.kt`; tests `app/src/test/.../DonutGeometryTest.kt`, `ThemeContrastTest.kt`, `app/src/androidTest/.../LayoutUiTest.kt`.
- `core/ui/Components.kt` (`EmptyState`, new `DialogColumn`, `TransactionRow`, `InfoBanner` radii), `Rows.kt`, `TabBar.kt`, `Gestures.kt` (radius token only), `core/theme/Color.kt` (income green).
- Screens: `AnalyticsScreen`, `AccountsScreen`, `DashboardScreen`, `TransactionsScreen`, `BudgetsScreen`, `SubScreens` (recurring), `SettingsScreen`, `CategoriesScreen`; radius tokens only in `CsvImportScreen`, `MoreScreen`, `ScanReceiptScreen`.
- `values/strings.xml`, `values-hu/strings.xml`: `analytics_donut_description`, `clear_search`, `close` (the translation parity test passes).
- Docs: `docs/TESTING.md`, `docs/MOTION_DESIGN.md` (new "Layout at large font scales" section), `README.md` (test counts).

### 15.5 Tests executed (actual results, 2026-10-11)
| Command | Result |
| --- | --- |
| `./gradlew cleanTestDebugUnitTest testDebugUnitTest lint assembleDebug connectedDebugAndroidTest --offline`, run once on the final code (exit 0) | BUILD SUCCESSFUL. Unit XML (19:11): **212 tests, 0 failures, 0 errors, 0 skipped** (198 + 11 `DonutGeometryTest` + 3 `ThemeContrastTest`). Instrumented XML on the API 35 emulator (19:12): **49 tests, 0 failures, 0 errors, 0 skipped** (36 + 13 `LayoutUiTest`). `app-debug.apk` rebuilt 19:10. |
| lint | Lint report on disk: **0 errors, 28 warnings, 1 hint**. Caveat (same as §14.2): the report file is time-stamped 19:07, i.e. from the run just before the last small edit (`AmountRowLayout` font-scale threshold, `Rows.kt`); `lint` and a forced `lintDebug --rerun` both exited 0 but did not rewrite the file, so the figure is not independently re-confirmed for that last edit. |

Between the earlier unit/lint/instrumented runs and the final one, the only source change was fix 18 (`Rows.kt`). Git status at the end of the session: modified files and the five new files listed in §15.4, plus the still-untracked `HANDOFF.md`; nothing staged or committed.

Test-design notes: `LayoutUiTest` reads text cut-off from the text layout (an ellipsis, or one line wanting more width than its box); `hasVisualOverflow` is useless for non-wrapping text. The tab-bar test was **verified to fail on the old tab bar and pass on the new one**. A locale override through `LocalContext` does not reach dialog windows, so the Hungarian test uses a row, not a dialog. Several first drafts of the new tests were wrong (not the code) and were fixed before being kept; no existing test was weakened, skipped or deleted. That a transfer never enters spending totals was already covered (`CategorizationTest`, `CurrencyAndStatsTest`, `CategorizationHarnessTest`, all passing) and the on-screen figures in §15.2 agree with it.

### 15.6 Visual and accessibility checks
**Performed** (emulator, 360 dp, synthetic data; screenshots were viewed during the session and are not stored in the repository):
- Font scale **1.0**: Dashboard, Analytics (full scroll, donut): the baseline, before the fixes.
- Font scale **1.5**, English, light: Dashboard, Transactions (+ detail sheet), Analytics (summary, transfers, donut, legend, merchants), More, Accounts, Budgets (+ dialog), Settings (top and middle), Stocks (empty), Import (start screen), Categories, Uncategorized (with data) + CategorizeDialog. The failing ones were looked at before and after the fix.
- Font scale 1.5, **Hungarian**, light: Dashboard, Transactions, Analytics. Hungarian, **dark**: Dashboard, Transaction detail sheet.
- Font scale 1.5, English, **dark**: Dashboard, Analytics with a selected category (centre text fits inside the ring).
- Font scale **1.3**: English light Dashboard and Analytics (donut with a 987,890,009 Ft total, legend before and after fix 18); Hungarian dark Transactions.
- By test: spoken descriptions of a transfer row (English and Hungarian), the donut description, tab labels not clipped (English, Hungarian), amounts whole in a narrow column and in dark.

**Not performed / unverified:**
- **TalkBack was not run**, on the emulator or on a device; only semantics assertions exist. Manual check: with TalkBack on, swipe through Analytics (the chart should read the total and each category with its share, then the legend rows as buttons), a transaction row (kind, name, amount, details as one item) and the detail sheet (close button named).
- Not looked at on screen: Review queue, Scan receipt, Notification automation, Import summary after a real import, Stocks with data and detail, Settings in Hungarian and dark, Accounts / Budgets / Categories in Hungarian and dark, dialogs in Hungarian, 1.3 on the screens not listed above, landscape or a small-height device, cutouts and system bars on a real phone, very long Hungarian words in the keyword chips.
- No physical device; no frame-rate measurements; the ML Kit runtime network behaviour from §14.6 is still unchecked.
- **Hungarian wording is not reviewed.** The privacy-policy text is still an unreviewed agent translation, and the three new Hungarian strings ("Kördiagram a költésekről kategóriánként. Összesen %1$s. %2$s.", "Keresés törlése", "Bezárás") are mine and unreviewed too. Nobody competent has reviewed any Hungarian wording in this stage; it must not be called linguistically verified.

### 15.7 Remaining issues and observations (not fixed)
- Tab labels shrink individually, so at large scales "Tranzakciók" is smaller than "Elemzés" (a shared size would need a shared minimum).
- The month navigator wraps "October 2026" onto two lines at 1.5 (nothing is cut); the in-page titles "Accounts", "Recurring", "Categories and rules" repeat the top-bar title.
- The scrolling area ends in a hard edge just above the floating tab bar (the bar floats over the page background; Phase 3's translucent chrome will change this).
- Category colours (amber, teal, …) are below 3:1 as graphics on the light card surface; they are always paired with a name and a share, so they were left as palette (a design decision).
- The Hungarian search placeholder is cut with an ellipsis at 1.5.
- Many plain spacing numbers (`16.dp`, `12.dp`, …) remain in screens other than the converted ones; only shared rows, chips, dialogs and the four corner radii were tokenised. Receipt, Notification automation, Stocks and Review screens were not restructured.
- The dashboard "Total balance" deliberately includes transfers and money sent to people (it is a balance, not spending); Analytics "Net" and all spending figures exclude them. No financial-correctness defect was found in this stage.

### 15.8 Unresolved questions for the user
1. Is it acceptable that the filters on Transactions scroll away with the list (instead of staying pinned), at the cost of the empty-state cross-fade?
2. Is the slightly darker income green (`#2B7549`) acceptable?
3. Should the text inside the donut hole and the three dashboard shortcuts stay capped at 1.3× (current), or should the chart and tiles grow instead?
4. Who can review the Hungarian wording (the privacy policy and the new strings)?

### 15.9 Is Phase 2 finished? Next action
**Not completely.** Code, unit tests, lint, the build and the instrumented tests are green, and the main screens were checked at 1.3 and 1.5 in both languages and both themes (see §15.6 for the exact combinations), but TalkBack, a physical device and the screens listed under "Not looked at" are unverified. The project is **ready for the user's review of Phase 2**; it should enter Phase 3 only after that review and the answers to §15.8.
Exact next action: (1) review the uncommitted diff (`git diff` plus the five untracked files in §15.4) and answer §15.8; (2) commit Phase 2 (for example "Fix large-font layout; add chip flow, fit-text, donut description and layout tests"), with or without `HANDOFF.md`; (3) do the manual checks in §15.6 (TalkBack; the unviewed screens); (4) then start Phase 3 as described in §2 (shared spring specs, shared-element transitions, predictive back, then translucent chrome).

## 16. Stage 3 report: motion and glass (2026-10-11)

Scope: Phase 3 only. Nothing was committed, pushed, reset or amended; **Stage 2 and Stage 3 work are both uncommitted** on `claude/categorization-layout-motion` (HEAD still `dbd31b5`). No dependency, BOM or build-configuration change. No categorization, transfer, currency, import, notification, receipt or analytics code was touched; no string was added (EN/HU parity unaffected).

### 16.1 Gate check before starting (verified)
Phase 2 had no critical regression and no financial-correctness defect: unit tests, lint and assemble were re-run green at the start (212/212), instrumented 49/49 (§15.5), transfers excluded from spending (§15.2, §14.3). Phase 2's open items were manual checks (TalkBack, physical device), not defects, so Phase 3 was started. The repository matched the handoff (same branch, HEAD, 20 modified + 5 untracked files).

### 16.2 What already existed (audited, not redone)
Spring tokens in one place (`Motion`), press feedback on touch-down on almost every tappable row, a spring tab indicator, `animateItemPlacement` on every reordering list, chart and progress geometry animation (amounts never animate), nav transitions (fade between tabs, short slide for drill-in, fade only for reduced motion and the camera), swipe actions with momentum, `enableOnBackInvokedCallback`, animator-scale handling. These cover most of the handoff's Phase 3 list already (`docs/MOTION_DESIGN.md`).

### 16.3 Changes made
| File | Change and reason |
| --- | --- |
| `core/ui/Glass.kt` (new) | `Glass` constants and `Modifier.glassChrome`: tinted fill at 94 %, top highlight, hairline, shadow; opaque and no highlight when the system contrast (Android 14+ `UiModeManager.contrast`) is medium or high. **No backdrop blur** (16.5) |
| `core/ui/TabBar.kt` | Surface replaced by `glassChrome`; the selected *label* is drawn in `onSurface` instead of terracotta (16.4) |
| `MainActivity.kt` | 16 dp fade of the content into the page just above the tab bar (draw phase only); removes the hard cut listed in §15.7 |
| `core/ui/Motion.kt`, `feature/analytics/AnalyticsScreen.kt` | `pressSelectable` (selectable + press scale), used for the Analytics legend rows, the one tappable row that lacked touch-down feedback |
| `app/src/test/.../GlassContrastTest.kt` (new, 4 tests) | Holds the bar's labels and icon to 4.5:1 / 3:1 over black, white and page backdrops, light and dark |
| `docs/MOTION_DESIGN.md`, `docs/TESTING.md`, `docs/REAL_DEVICE_TEST_PLAN.md` (new §7), `README.md` | Glass section, "Not done and why" table, test counts, physical-device measurement steps |

### 16.4 Defect found on the way
The selected tab label (terracotta on its 12 % terracotta pill) was **4.07:1 in the light theme even before the glass** (opaque bar), under the 4.5:1 bar for small text; `ThemeContrastTest` did not cover the pill. Fixed as above and guarded by `GlassContrastTest`. With the 94 % glass the old label would have dropped to 3.6:1 over a black backdrop.

### 16.5 Decisions and trade-offs
- **Glass is limited to the tab bar.** Cards, sheets, dialogs and the title bar are unchanged: amounts and category labels sit on cards, and nothing was gained by making them translucent.
- **No backdrop blur and no library.** Compose 1.6 cannot blur what is behind a node. The options were a library that needs a BOM upgrade (Haze) or a `RenderNode` + `RenderEffect` workaround that draws every scrolling screen twice and could only be judged on an emulator. The simpler tinted bar never lets text show through. This departs from the handoff's wording ("real blur only on API 31+"); it is deliberate. Revisit after profiling on a physical device.
- **Not done:** in-app predictive back that follows the finger, shared-element/container transforms (both need Navigation 2.8 / Compose 1.7), `animateItem` (this BOM has `animateItemPlacement`, already used), counting numbers (deliberately never), baseline profile, Analytics month slide. Reasons are in `docs/MOTION_DESIGN.md`.
- Content does not scroll *under* the bars (it ends above the tab bar, as before), so the bar's translucency shows almost only the page background; the visible effects are the tint, highlight, edge and the fade. Letting lists run under the bar would need top/bottom content padding in about 16 scroll containers; not done (scope).

### 16.6 Tests executed (actual results)
| Command | Result |
| --- | --- |
| `./gradlew cleanTestDebugUnitTest testDebugUnitTest lint assembleDebug connectedDebugAndroidTest --offline`, one run on the final code (exit 0, "BUILD SUCCESSFUL in 1m 48s") | Unit XML: **216 tests, 0 failures, 0 errors, 0 skipped** (212 + 4 `GlassContrastTest`). Instrumented XML, API 35 emulator: **49 tests, 0 failures, 0 errors, 0 skipped** (no new instrumented tests). |
| lint | The old report was deleted first, so the new file (19:48) is real: **0 errors, 28 warnings, 1 hint**, the same as before. This also settles the §14.2 doubt about the figure. |
| first `GlassContrastTest` run | **failed** (selected label 4.06:1 over black). That is how 16.4 was found; the design was changed and the test rewritten for it, not loosened. |

### 16.7 Visual and accessibility checks
**Performed** (emulator `finances_api35`, 360 dp, "Add demo data"; screenshots were viewed during the session and are not stored in the repository):
- Light, font scale 1.0: Overview (before and after), More (list ending above the bar, and scrolled), Overview scrolled with cards under the fade; the tab bar and the new selected-label colour.
- Dark, font scale 1.5: Overview scrolled; tab labels legible and not cut; the fade matches the dark page.
- Animations: a tab switch captured 120 ms after the tap, system animator scale 1 vs 0. At 1 the capture is mid cross-fade with the tab pill mid-slide; at 0 the Analytics screen and the pill are fully settled. Two earlier captures of this check raced the tap and proved nothing; they were discarded and the check was redone.
- By test: tab-bar label contrast over worst-case backdrops (light, dark); the existing `LayoutUiTest` tab-label clipping tests still pass with the new bar.

**Not performed / unverified:**
- Hungarian screenshots of the new bar (only the existing clip test covers it), dark at 1.0 and light at 1.5 on screen, Transactions/Analytics scrolled under the fade in dark.
- The raised-system-contrast opaque path (`UiModeManager.contrast`) was **never run**: it is compiled and lint-clean only.
- Press scale on the Analytics legend rows: compiled and the suites pass, but I did not watch the press on screen.
- TalkBack not run (unchanged from §15.6). No physical device and **no frame-rate or jank measurement** (steps are in `docs/REAL_DEVICE_TEST_PLAN.md` §7). Predictive back not exercised on a gesture-navigation device.
- ML Kit runtime network behaviour (§14.6) and the Hungarian wording are still unreviewed. **Privacy-policy review still outstanding:** no data practice changed in Phase 3 (no new permission, network call or storage), so the policy needs no Phase 3 edit, but its Hungarian translation still needs a native reviewer.

### 16.8 Remaining issues
- Tab labels still shrink individually at large font scales ("Transactions" smaller than "Overview"), as in §15.7.
- Everything in §15.7 other than the hard edge above the tab bar (now faded) is unchanged.
- The questions in §15.8 (scrolling filters, income green, 1.3 cap, Hungarian review) are unanswered. The new selected-label colour is one more visual decision for the user to accept.

### 16.9 Git state at the end of Stage 3
Modified, all uncommitted: the files of §15.4 plus `core/ui/Motion.kt`, `MainActivity.kt` and `docs/REAL_DEVICE_TEST_PLAN.md`; `AnalyticsScreen.kt`, `TabBar.kt`, `docs/MOTION_DESIGN.md`, `docs/TESTING.md` and `README.md` carry both Stage 2 and Stage 3 edits. Untracked: `Glass.kt`, `GlassContrastTest.kt` (Stage 3) and `Chips.kt`, `FitText.kt`, `DonutGeometryTest.kt`, `ThemeContrastTest.kt`, `LayoutUiTest.kt`, `HANDOFF.md` (Stage 2 and the handoff). Nothing staged or committed.

### 16.10 Ready for a broader release-readiness review?
**Almost, not yet.** Code, unit tests, instrumented tests, lint and the build are green and no financial behaviour changed. Before a release review: (1) the user reviews and commits Stage 2 and Stage 3 (suggest two commits: layout/accessibility, then motion/glass); (2) answer §15.8; (3) run the physical-device plan, including §7 frame measurements, TalkBack, and the raised-contrast and Hungarian checks above; (4) native review of the Hungarian text and the privacy policy; (5) `lintRelease` / `assembleRelease` with a signing key (not run in this stage); (6) only then decide whether a backdrop blur or the Compose 1.7 upgrade (predictive back, shared elements) is worth doing.

## 17. Stage 3.1: audit follow-up (2026-10-11)

Scope: the limited clean-up pass that followed the Stage 3 completion review. No commit, push, reset, rebase, stash or clean; no dependency, BOM, Gradle, manifest or schema change; no domain, data, categorization, transfer, currency, import, notification or receipt code touched. Stage 2, Stage 3 and Stage 3.1 work are all still uncommitted on `claude/categorization-layout-motion` (HEAD `dbd31b5`).

### 17.1 Decisions from the user, and what was done
| Decision | Result |
| --- | --- |
| Keep the tinted glass tab bar, no blur, no new dependency | Unchanged. |
| Respect system font scaling; investigate the 1.3 caps | **Both caps removed** (17.2). |
| Transactions search and filters: keep accessible while scrolling without costing screen space | **Quick-return header** (17.3). |
| Keep the darker income green only if contrast holds | `ThemeContrastTest` (income `#2B7549` against the page and the card surface, light and dark, 4.5:1) passed in the final run. Unchanged. |
| Keep the main-text selected tab label | Unchanged; `GlassContrastTest` passed. |
| Do not rewrite the privacy policy | Not touched. No data practice changed in this stage. |

### 17.2 Font-scale caps removed
- **Donut centre** (`feature/analytics/AnalyticsScreen.kt`): the `LocalDensity` override capped at 1.3 is gone. The text follows the user's font scale; the ring grows with it instead (`donutRingSize`: 220 dp at 1.0, plus 60 dp per extra 1.0 of scale, at most 280 dp, which fits a 360 dp card and shrinks to the card on narrower screens). `donutHoleDiameter` and `donutCentreWidth` size the text column from the hole. The label stays one line (ellipsized in the hole; the legend shows the full name), and the total uses `ShrinkToFitText`. The column's `CircleShape` clip, which nicked the label's left edge on screen, was changed to `Radii.control`.
- **Dashboard shortcuts** (`feature/dashboard/DashboardScreen.kt`): the cap is gone. New `QuickActions` / `QuickActionItem`: side by side while the longest word of every label (measured with the real style) fits its tile, otherwise stacked as full-width rows. A label wraps at spaces only. Replaces the private `QuickAction`.
- This supersedes §15.3 items 6 and 13 and the "capped at 1.3" remarks in §15.7, §15.8 and §16.8.

### 17.3 Transactions header
New `core/ui/QuickReturn.kt` (`QuickReturnState`, `QuickReturnScaffold`). The search field and the period chips sit over the top of the list: shown at the top, hidden after about 24 dp of scrolling down, back on the first scroll up by the same amount, and kept in place while the search field has focus. The sort and count row and the transactions stay in the list. Search and filter behaviour (`TransactionsViewModel`) is unchanged.
Trade-offs: the header covers the first rows while it is shown after a scroll up (about a fifth of the screen at font scale 1.5, from the screenshot); a hidden header is still in the accessibility tree (not verified with TalkBack); the empty/non-empty cross-fade dropped in Stage 2 stays dropped.
**Defect found by the new test:** the first version moved the header with `graphicsLayer`. The instrumented test reported the header still displayed after scrolling, because accessibility bounds do not follow a graphics layer: a hidden header would still have claimed the top of the list for TalkBack and tests. It now moves with a placement offset (`Modifier.offset { }`) and the test passes.

### 17.4 Other fixes
- Dashboard category rows used `percentage.toInt()%` (truncating, not locale aware); they now use `CurrencyFormatter.formatPercent`, as Analytics does. Visible change: "88%" becomes "88.8%" (Hungarian "88,8%").
- `LayoutUiTest`: the donut description test no longer hard-codes English or "83.0%"; the expected string comes from the string resource in an explicit English locale and from `CurrencyFormatter`. The English transfer-row test now composes in `Locale.ENGLISH` explicitly.
- Docs: README unreleased paragraph and counts (the "minified release build" wording is now backed by a run, with a throwaway key: 17.5); duplicate emulator bullet removed and the layout rules rewritten in `docs/MOTION_DESIGN.md`; `docs/TESTING.md` tables and a Stage 3.1 paragraph; §3, §6, §9 and §11 of this file marked historical or corrected.

### 17.5 Tests executed (actual results, this machine, 2026-10-11)
| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --rerun-tasks --offline` (emulator `finances_api35`, density and font scale reset to defaults first) | BUILD SUCCESSFUL in 2m 15s, 88 tasks executed. Unit tests **224 passed, 0 failed, 0 skipped** (216 plus 8 new in `QuickReturnAndDonutLayoutTest`). Instrumented **59 passed, 0 failed, 0 skipped** (49 plus 10 new in `LayoutUiTest`). Lint (debug) **0 errors, 28 warnings, 1 hint**, as before. |
| `./gradlew lintRelease assembleRelease --rerun-tasks --offline`, with `FINANCES_KEYSTORE_PATH`, `FINANCES_KEYSTORE_PASSWORD`, `FINANCES_KEY_ALIAS` and `FINANCES_KEY_PASSWORD` set to a **throwaway keystore generated outside the repository** (the project refuses release tasks without credentials, by design; Gradle was not changed) | BUILD SUCCESSFUL in 1m 14s, 57 tasks executed. Release lint 0 errors, 28 warnings, 1 hint. The APK is minified and signed with the throwaway key only: not a distributable release, and it was **not installed or run**. |
| `git diff --check` | Exit 0 (only the LF to CRLF warnings). |
| Earlier in the pass | One instrumented run failed (the header test, 17.3) before the offset fix; it was fixed in code, not weakened. |

### 17.6 Visual checks (emulator, 360 dp, font scale 1.5, English, light, demo data)
Viewed: Dashboard (empty state; shortcuts side by side, nothing cut); Transactions at the top, scrolled down (header gone) and scrolled up a little (header back, search and chips only); Analytics donut with and without a selected category (text at full 1.5 scale inside the hole, long label ellipsized).
**Not viewed on screen:** the `Radii.control` clip fix (made after the screenshots), Hungarian, dark, font scales 1.0 and 2.0 (tests only), the Dashboard with data (so the new percentage format), and the stacked shortcut layout (no width that needs it was on screen; the tests cover 1.5 and 2.0). The emulator density and font scale were reset afterwards and the emulator was stopped.
Observation: while the Transactions list is scrolled, the floating add button covers part of a row's amount (it only clears at the end of the list because of the 88 dp spacer). That is the existing floating-button design, not new.

### 17.7 Git state, remaining limitations, next step
- Modified (24): README.md, MainActivity.kt, Color.kt, Components.kt, Gestures.kt, Motion.kt, Rows.kt, TabBar.kt, AccountsScreen.kt, AnalyticsScreen.kt, CategoriesScreen.kt, DashboardScreen.kt, CsvImportScreen.kt, MoreScreen.kt, ScanReceiptScreen.kt, SettingsScreen.kt, BudgetsScreen.kt, SubScreens.kt, TransactionsScreen.kt, both `strings.xml`, docs/MOTION_DESIGN.md, docs/REAL_DEVICE_TEST_PLAN.md, docs/TESTING.md. Untracked (10): HANDOFF.md, Chips.kt, FitText.kt, Glass.kt, **QuickReturn.kt**, DonutGeometryTest.kt, GlassContrastTest.kt, ThemeContrastTest.kt, **QuickReturnAndDonutLayoutTest.kt**, LayoutUiTest.kt. Stage 3.1 touched AnalyticsScreen.kt, DashboardScreen.kt, TransactionsScreen.kt, QuickReturn.kt, LayoutUiTest.kt, QuickReturnAndDonutLayoutTest.kt, README.md, MOTION_DESIGN.md, TESTING.md and this file.
- Still open: TalkBack never run (including whether a hidden Transactions header confuses it); no physical device and no frame-rate measurements; the raised-contrast tab bar never run; Hungarian wording and the Hungarian privacy policy unreviewed; Hungarian and dark screenshots of the screens in §15.6 "Not looked at"; tab labels still shrink individually; the quick-return header is tested on the scaffold with a plain list, not on the real Transactions screen; the donut's centre tap uses a fixed 0.6 radius, smaller than the hole at large scales (harmless: the legend rows also clear the selection).
- Recommended next step: review the diff and commit in two or three commits (layout and accessibility; motion and glass; follow-up), then run the physical-device plan with TalkBack and a properly signed `assembleRelease`.
