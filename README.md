# TERA — offline envelope budgeting for Android

TERA is a native Android envelope-budgeting app with **no network code at all**: no accounts,
no sync, no analytics, and no permissions declared in the manifest. Everything lives in a local
SQLite database on the phone.

The budget model is envelope budgeting: Ready to Assign, envelope rollover, credit card
Payment categories with a real cash reserve, targets, Auto-Assign, Money Moves, reconciliation and
the reporting suite — while deliberately leaving out everything that needs the internet.

---

## Install (sideload)

1. Copy `dist/TERA-debug.apk` to your phone (USB, cloud drive, anything).
2. Tap it. Android will ask to allow installs from that app — allow it once.
3. Open **TERA** and complete the short setup (currency, first account, starter envelopes).

Requires Android 8.0 (API 26) or newer. The APK is universal: there are no native libraries, so
the same file works on every phone.

| | |
|---|---|
| File | `dist/TERA-debug.apk` (16.6 MB) |
| Package | `com.tera.app` 1.0 (versionCode 1) |
| SDK | min 26, target/compile 35 |
| SHA-256 | `9DE6CB55894604A9DCBCEF70653401570A5A5AB1D1760509200DEC03684C4CDB` |
| Signed by | `keystore/debug.keystore` (same key as `dist/debug.keystore`) |

**Permissions.** The app declares **no** `INTERNET` permission and holds no sensitive
permissions. The only entry in the manifest is `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, a
signature-level permission that AndroidX's own libraries define for themselves; it grants no
capability. Everything the app does is local file I/O through the system file picker.


### Updating later

`dist/debug.keystore` **is the signing key for this APK**. Any future build must be signed with it,
otherwise Android treats the new APK as a different app and refuses to update — you would have to
uninstall, which deletes your budget. Keep `debug.keystore` with the APK (it is also committed to
this repository on purpose). If you build on another machine, copy the keystore along.

---

## What it does

### Budget
- **Ready to Assign** with a full breakdown: carried in, income, tracking transfers, assigned,
  overspent last month.
- TERA rows showing **Assigned / Activity / Available**, with group subtotals and a grand total.
- **Auto-Assign**: underfunded, last month's assigned, last month's spending, 3-month average, reset.
- **Money Moves** between any two envelopes (including card Payment envelopes) or back to Ready to
  Assign.
- **Targets** on categories: Needed for Spending (optionally by a date), Savings Balance, Monthly
  Savings Builder — with one-tap funding, progress bars and per-month snooze.

### Credit cards (full reserve model)
- Every credit card gets an automatic **Payment** envelope.
- A categorized purchase moves money from the spending envelope into the Payment envelope — but only
  up to what the spending envelope actually holds.
- Paying the card is a transfer; it releases the reserve and reduces the debt.
- Payment envelopes show **green** (fully funded), **yellow** (underfunded: starting balance,
  unfunded spending, interest, balance transfer) or **red** (you paid more than you had reserved).
- Rewards/overpayment (`balance ≥ 0`) reserves nothing until real debt reappears.
- Balance transfers move debt between cards without moving payment money — you fund the destination.

### Accounts and transactions
- Checking, savings, cash, credit cards, plus **off-budget tracking accounts** for investments and
  mortgages (they count toward net worth, not toward the budget).
- Register with running balance, cleared/reconciled states, colour flags, memos and search.
- **Transfers** are stored as a linked pair and always edited/deleted together.
- **Reconcile** against a real statement balance; the difference is booked to a hidden Adjustment
  category.
- Scheduled ("Upcoming") transactions with weekly → yearly recurrence and one-tap **Enter now**.

### Reflect
- Spending by category for the month.
- Net worth over the last 12 months, split into assets and liabilities.
- Income vs expense over the last 12 months.
- **Age of Money** and **Days of Buffering**, with their definitions shown in-app.
- Target progress across all envelopes.

### Data
- **JSON backup / restore** via the system file picker. The backup includes settings and carries a
  `schemaVersion`, so future versions can migrate it.
- **CSV export** of all transactions (RFC 4180 quoting, UTF-8 BOM so Excel opens it correctly).
- No auto-backup, no cloud, no device transfer.

---

## How the money math works

All amounts are integer cents; no floating point is used for money. The engine is pure Kotlin and
recomputes the whole month history from the ledger on every change, so the ledger and the budget can
never drift apart.

- `Available = Assigned + Activity + Rollover in`
- Positive Available rolls into the next month. When a month closes:
  - **Deduct-from-next-month mode (default)** — cash overspending is subtracted from next month's Ready to Assign;
    card overspending rides as debt and leaves the Payment envelope underfunded.
  - **Carry-negative mode** — the negative Available carries forward as a red envelope instead.
- Card reserve: `reserve = min(debt increase, funded portion of the category)`.
- Transfers: budget ↔ budget is invisible to the budget; budget → tracking is spending; tracking →
  budget is income; card payments release reserve; card ↔ card moves debt only.
- **Age of Money** averages the last 10 cash outflows, FIFO-matched against inflow lots. Card
  purchases do not age money — it ages in the Payment reserve until the card is paid.
- **Days of Buffering** = positive budget-account balances ÷ average daily spending over 90 days
  (tracking accounts and negative card balances excluded).

The in-app screen **More → How TERA calculates things** repeats these definitions so the model's
limits are never hidden.

---

## Project layout

```
app/src/main/java/com/tera/app/
  domain/      pure engine: BudgetCalculator, Metrics, TargetMath, TransferClassifier, models, enums
  data/        Room entities + DAOs, repository, backup, settings
  ui/          Compose screens (budget, accounts, editor, upcoming, reflect, settings, onboarding)
  util/        money formatting and ISO dates
app/src/test/java/com/tera/app/    JVM unit tests for the engine, metrics, targets, formatting
app/schemas/                               exported Room schema (migration baseline)
keystore/debug.keystore                    the signing key — keep it
```

If you are an AI agent (or a human picking this up cold), read **AGENTS.md** first: it records the
engine invariants, the environment quirks of the original build machine, and the publishing flow.

## Build from source

Requirements: JDK 17 and Android SDK platform 35 + build-tools 35.0.0.

**Android Studio** — open this folder, let Gradle sync, press Run. Android Studio's bundled JDK is
sufficient.

**Command line**

```bash
./gradlew test          # engine unit tests
./gradlew assembleDebug # app/build/outputs/apk/debug/app-debug.apk
```

Pinned versions live in `gradle/libs.versions.toml` (Kotlin 2.0.21, KSP 2.0.21-1.0.28, AGP 8.7.3,
Gradle 8.9, Room 2.6.1, Compose BOM 2024.10.01).

If Gradle complains that `gradle-wrapper.jar` is missing, point Android Studio at a local Gradle
distribution (Settings → Build Tools → Gradle → Use Gradle from: specified location), or run
`gradle wrapper --gradle-version 8.9` once with any Gradle 8.x install.

## Tests

`./gradlew test` runs the JVM suite (48 tests, all green on the build that produced this APK):

- card reserve: funded purchases, payments, overpayment (red), carried debt, rewards balances,
  refunds, cashback, interest, balance transfers;
- overspend routing in both modes, and mixed cash/card funding inside one envelope;
- the full transfer matrix, both directions;
- Ready to Assign across months and the overspend deduction line;
- Age of Money fixtures with pinned expected days, and Days of Buffering excluding card debt;
- all three target types, by-date rounding and snooze;
- money formatting and parsing.

## Limitations (deliberate, for v1.0)

Split transactions, bank import, device sync, shared budgets, loan payoff calculators, widgets and
notifications are not implemented. The app is single-device and single-budget. Credit-card handling
treats each card's Payment category as described above; there is no separate legacy-debt concept
beyond the starting balance staying yellow until you fund it.

## Troubleshooting

- **"App not installed"** — an older build with a different signature is present. Uninstall it (this
  deletes its data — restore from a JSON backup afterwards).
- **Gradle cannot reach Google/Maven** — the project itself needs no network, but the first sync
  downloads dependencies. Check proxy/TLS settings in Android Studio.
- **OneDrive folder** — this project may live in a OneDrive-synced folder; Gradle writes build output
  under `app/build/`, which OneDrive will sync. Moving the project to a non-synced path avoids that.
