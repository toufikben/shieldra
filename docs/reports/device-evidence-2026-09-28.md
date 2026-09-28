# Device evidence — owner Realme RMX3910, 2026-09-28

**How obtained:** APKs downloaded from CI run `36425331428` (commit `70e62e4`)
via the new `apks-<run>` artifact, installed with
`adb install -r -t`, executed with
`adb shell am instrument -w com.shieldra.app.test/androidx.test.runner.AndroidJUnitRunner`.
No local Gradle build was used.

**Device:** `RMX3910` (serial `IR7X5DJ7KZGEHMS8`), `adb devices` → `device`.
**StrongBox feature flag on this device:** `android.hardware.strongbox_keystore` = false
(measured 2026-09-27, still the recorded device fact).

## Measured result — second run, after the classifier fix

APKs from CI run `36428989303` (commit `a26335d`, 112/112 JVM tests green):

```
Time: 8.305

OK (22 tests)
```

| Suite | Result |
| --- | --- |
| `RealAesGcmAadTest` (real AES-GCM + AAD binding + StrongBox detection) | passed |
| `RoomEventRepositoryInstrumentedTest` | passed |
| `ShieldraDatabaseMigrationInstrumentedTest` (v1 → v2 + factory wiring) | passed |
| `StorageRecoveryInstrumentedTest` (real Room downgrade and migration-failure wording) | passed |

See **Operational note for future device runs** below for the signing
constraint that decides which APK pair can be installed together.

## Measured result — first run

```
Time: 2.049
FAILURES!!!
Tests run: 22,  Failures: 1
```

| Suite | Result |
| --- | --- |
| `RealAesGcmAadTest` | 11 tests passed |
| `RoomEventRepositoryInstrumentedTest` | 7 tests passed |
| `ShieldraDatabaseMigrationInstrumentedTest` | 2 tests passed |
| `StorageRecoveryInstrumentedTest` | 1 passed (downgrade), **1 failed (migration failure)** |

This is the first executed device evidence for the Room migration and
StrongBox-detection suites; the earlier 2026-09-27 note (7/7 Room, 11/11
AES-GCM/AAD) is reproduced by this run.

## The failure, and what it proved

```
java.lang.AssertionError: Room's real migration failure wording must be
recognised, not degraded to an unknown state. Actual detail:
android.database.sqlite.SQLiteException: duplicate column name: key_version
(code 1 SQLITE_ERROR): , while compiling: ALTER TABLE event_evidence_refs
ADD COLUMN key_version TEXT NOT NULL DEFAULT 'v1'. Actual: UNREADABLE_STATE
```

The test was written to fail if the classifier did not recognise real Room
wording, and it did fail — which is exactly the point of §4.6. Two facts came
out of it:

1. **The `DOWNGRADE` marker is correct.** Room's genuine downgrade failure
   matched and produced `RecoveryRequired(DOWNGRADE)` with the database file
   and its `user_version` untouched.
2. **The `MIGRATION_FAILURE` markers were invented, and wrong.** On device, a
   failed migration surfaces as the raw `SQLiteException` from the `ALTER
   TABLE`, whose text contains no word resembling "migration". Before the fix
   this degraded to `UNREADABLE_STATE` — still `RecoveryRequired`, still
   non-destructive, but a less informative reason.

Fixed in `a26335d` by adding the measured SQLite wording
(`duplicate column name`, `while compiling`, `no such table`,
`no such column`) to the `MIGRATION_FAILURE` branch, with the exact device
message pinned as a JVM test case.

## Correction to a claim made during this session

While looking for why device tests had never been run, I concluded that
`testInstrumentationRunner` had been deleted in commit `96a5e6e`
(2026-09-21) and that this made the instrumented suites unrunnable. **That
conclusion was wrong and is withdrawn.** The measured fact: the suites
executed on device through `am instrument` with
`androidx.test.runner.AndroidJUnitRunner` even though the property is unset,
so the merged androidTest manifest does declare an instrumentation component.
Why it does so was not verified (the AndroidX test runner AAR manifest is the
likely source). `testInstrumentationRunner` remains unset in
`app/build.gradle.kts`, which is still worth adding for
`./gradlew connectedDebugAndroidTest`, but nothing about it is broken today.

## Operational note for future device runs

CI regenerates the Gradle debug keystore on each runner, so APKs from two
different runs are signed with different keys and `am instrument` then fails with
`SecurityException: ... does not have a signature matching the target`. Install
the app APK and the androidTest APK **from the same run**, after uninstalling any
build from an earlier run.

## Still open after this run

- Rotation against a real Android Keystore key (`shieldra_evidence_v1` → `v2`)
  is still not exercised by any instrumented test — the 22 passing tests cover
  storage, AAD, migration, recovery and StrongBox detection, not Keystore
  re-encryption.
- G4 remains not accepted.
