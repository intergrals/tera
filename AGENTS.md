# AGENTS.md — read this first

Context for any AI agent (or human) picking this repository up cold. It captures the invariants that
are easy to break, the environment gotchas, and where the bodies are buried. Keep it current: if you
change a rule described here, update this file in the same commit.

## What this is

**TERA** is a native Android envelope-budgeting app — offline only. No accounts, no sync, no analytics,
and **no `INTERNET` permission** (keep it that way). All state lives in a local SQLite database.

- Repo: https://github.com/intergrals/tera
- App id / package: `com.tera.app` (namespace and `applicationId` are the same)
- Version 1.0 (versionCode 1); release tag `v1.0` carries `TERA-debug.apk`
- `minSdk 26`, `targetSdk`/`compileSdk 35`, Java 17
- Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · Room 2.6.1 + KSP 2.0.21-1.0.28 · Compose BOM 2024.10.01
  (all pinned in `gradle/libs.versions.toml` — change versions there, nowhere else)

## Golden rules (breaking these is a bug, not a style choice)

1. **Money is `Long` cents everywhere.** No `Float`/`Double` in money math, not even transiently.
2. **The engine is pure.** `domain/` has no Android imports, no I/O, no clock reads except where a date
   is passed in. All budget output is *derived* from `BudgetInput`; nothing derived is ever stored.
   If you find yourself caching an "available balance" in a table, stop.
3. **Deterministic ordering.** Any stateful pass walks transactions in `(date, id)` order. Tests depend
   on it and the card reserve math is order-sensitive.
4. **No network, no new permissions.** Do not add retrofit/okhttp/analytics/permissions. The backup and
   CSV paths go through SAF (`CreateDocument`/`OpenDocument`).
5. **The in-app disclosure must match behaviour.** `SettingsScreen.kt` → `DisclosureScreen` documents
   Age of Money, Days of Buffering, card reserve, transfer handling and the deferred features. If you
   change any of those semantics, update that screen in the same change. It exists so the model's
   limits are never hidden.
6. **The keystore must survive.** `keystore/debug.keystore` signs every APK. Lose it and the next build
   cannot update-install over the previous one (Android forces an uninstall = data loss).

## Where things are

```
app/src/main/java/com/tera/app/
  domain/     pure engine — READ THIS BEFORE CHANGING MONEY BEHAVIOUR
    BudgetCalculator.kt   month-by-month replay: Ready to Assign, rollover, card reserve, transfers
    Metrics.kt            Age of Money (FIFO) and Days of Buffering
    TargetMath.kt         the three target types
    TransferClassifier.kt the 3x3 transfer matrix
    Models.kt / Enums.kt  input/output shapes and enums (enums are persisted by name)
  data/
    db/                   Room entities, DAOs, exported schema (app/schemas/**/1.json)
    BudgetRepository.kt   the ONLY write path; owns transfer-pair and system-category invariants
    BackupRepository.kt   JSON backup/restore (org.json, schemaVersion) + CSV export
    SettingsRepository.kt SharedPreferences (currency, overspend mode, onboarding)
  ui/                     Compose screens; each screen file owns its ViewModel
  util/                   MoneyFormat (deterministic formatting/parsing), Dates
app/src/test/java/com/tera/app/   JVM tests: engine, metrics, targets, money formatting
tools/ (none yet)                 put one-off scripts in %TEMP%, not the repo
```

## The money model in one page

- `Available = assigned + activity + rolloverIn` (accumulated during the month by `runningAvailable`).
- `ReadyToAssign(M) = RTA(M-1 carried) + income(M) + trackingNet(M) - assigned(M) - overspentLastMonth(M-1)`.
- **Month close**, `OverspendMode.DEDUCT_FROM_NEXT_MONTH` (default): positive Available rolls forward;
  negative *cash* shortfall is summed and deducted from next month's Ready to Assign; the category
  resets to zero. `OverspendMode.CARRY_NEGATIVE`: the negative carries forward as a red envelope and
  Ready to Assign is untouched. Card shortfall never touches Ready to Assign in either mode.
- **Card reserve is funding-capped.** For a categorized card outflow,
  `reserve = min(debtIncrease, fundedFromCategory)` where `fundedFromCategory = amount - overflow`.
  For a card inflow (refund) the reserve moves by the full (negative) debt delta. Uncategorized card
  inflows are pure Ready to Assign income and never touch the reserve.
- **Payment states:** `available < 0` → RED; `debt == 0` → GREEN; `available < debt` → YELLOW; else GREEN.
  Payment categories always carry their balance (a negative reserve is a real deficit).
- **Transfers** (see `TransferClassifier`): budget↔budget is invisible; budget→tracking is spending;
  tracking→budget is income; budget→card is a payment (reserve −); card→tracking behaves like a payment
  funded off-budget; card↔card moves debt only; tracking↔tracking does nothing.
- **Age of Money:** cash outflows only (budget cash accounts, card payments, transfers to tracking).
  Card purchases do not age money — it ages in the Payment reserve until the card is paid. Inflow lots
  are consumed FIFO over all history; the reported number averages the **last 10** cash outflows.
- **Days of Buffering:** `Σ max(0, balance)` over *budget* accounts (negative card balances and tracking
  accounts excluded) ÷ average daily outflow over the trailing 90 days.

## Data model notes

- Tables: `accounts`, `category_groups`, `categories`, `target_snoozes`, `budget_allocations`,
  `transactions`, `scheduled_transactions`. Settings live in SharedPreferences, not a table.
- **System categories** are undeletable and unpickable: `ADJUSTMENT` (reconcile target, hidden) and one
  `CARD_PAYMENT` category per credit card (linked via `linkedAccountId`).
- Transfers are two rows linked by `transferPeerId`, always the same date and amount, always created /
  edited / deleted as a pair inside `BudgetRepository`.
- `app/schemas/**/1.json` is the exported Room schema — commit it. It is the baseline for the first
  migration; `exportSchema = true` and the `room.schemaLocation` KSP arg must stay.
- Backup JSON carries `schemaVersion` (currently 1) plus settings. Restore validates the whole document
  (including referential integrity and transfer pairs) before replacing everything in one transaction.

## Build and test

```bash
./gradlew test            # 48 JVM tests; must stay green
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Environment notes learned the hard way on the original build machine (Windows, DSH sandbox):

- Kotlin/KSP run **in-process** (`kotlin.compiler.execution.strategy=in-process`) and file watching is
  off (`org.gradle.vfs.watch=false`) — separate compiler daemons and the native watcher hang there.
- Building inside a OneDrive-synced folder is extremely slow. Build a copy outside the sync root
  (`robocopy /MIR` to `%TEMP%\eprj`) and copy the APK back.
- There is no `gh` CLI, and Git Credential Manager (the system `credential.helper`) does not work inside
  the sandbox. Pushes authenticate with a machine-local **SSH key**, never a token: `~/.ssh/id_ed25519`
  (ED25519, no passphrase, ACL-restricted to the current user) with its public half registered on the
  GitHub account. `~/.gitconfig` rewrites `https://github.com/intergrals/` to
  `git@github.com:intergrals/`, so existing HTTPS remotes work unchanged, and sets
  `core.sshCommand = C:/Windows/System32/OpenSSH/ssh.exe` — git's bundled MSYS ssh cannot create its
  signal pipe under the sandbox (`Win32 error 5`), while the native OpenSSH client can. Verify with
  `ssh -T git@github.com`; if it answers `Permission denied (publickey)`, the public key is missing from
  the account, not broken locally. Never write tokens into `.git/config`, never paste one into a tracked
  file, and never switch back to the ephemeral `http.extraheader` trick.
- The signing key is project-local: `app/build.gradle.kts` points `signingConfigs.debug` at
  `rootProject.file("keystore/debug.keystore")` because the default `~/.android` location is outside
  the write sandbox on that machine.

## Conventions

- Compose Material 3, one screen per file with its ViewModel in the same file; manual DI via
  `AppContainer` + `LocalAppContainer` (no Hilt). `vmFactory { ... }` builds ViewModels with args.
- Room `Flow`s are combined in `BudgetRepository.inputFlow`, then fed to the pure calculator; UI state is
  a `StateFlow` produced by `combine` in each ViewModel.
- User-visible failures are `DomainException` → snackbar. Don't crash the app for validation.
- Strings are inline in composables (no string resources beyond `app_name`); keep copy plain and
  concrete, and never claim a feature that isn't implemented.
- Enums are persisted **by name**; renaming a constant silently changes stored meaning (a stale value
  falls back to the `fromStorage` default — acceptable only when the default means the same thing).

## When you change the engine

1. Add or extend a test in `app/src/test/.../BudgetCalculatorTest.kt` (or `MetricsTest`/`TargetMathTest`)
   that fails before your change and passes after.
2. Keep the `(date, id)` ordering and the funding-cap rule intact unless the change is precisely about
   those, in which case update AGENTS.md and the disclosure screen too.
3. Re-run the full suite; the card-flow tests are the canary.

## Deferred / next steps

Split transactions (schema baseline is exported; needs a v1→v2 migration), bank import, device sync,
shared budgets, loan payoff calculator, home-screen widgets, notifications, and a `tools/` folder for
one-off scripts. Reminders: the app is intentionally single-device and single-budget.

## Credit where the model came from

Envelope budgeting is a decades-old method (paper envelopes, then spreadsheets). TERA implements it
with its own terminology and its own code; no third-party app's assets, text or code are used.

## Publishing

- Push to `main`; tag releases `vN` and attach `TERA-debug.apk` as a release asset (the APK is not
  committed — `dist/` is gitignored).
- Keep `README.md` (user-facing) and this file (agent-facing) in sync with behaviour.
