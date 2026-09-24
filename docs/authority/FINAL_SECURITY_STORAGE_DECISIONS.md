# SHIELDRA — FINAL SECURITY STORAGE DECISIONS

SOURCE OF TRUTH — IMPLEMENTATION MANDATORY

هذه الوثيقة تعتمد القرارات التالية بشكل نهائي.

ممنوع على AI GitHub إعادة فتح هذه القرارات أو اختراع سياسة بديلة.

الهدف ليس فقط تعديل الكود، بل إغلاق جميع المراحل المرتبطة بهذه القرارات مع:

- implementation
- tests
- static analysis
- Android/instrumented tests
- build
- regression audit
- security audit
- documentation
- roadmap
- commit
- push
- remote verification
- CI verification

إذا اكتشفت أن قرارًا ما يتعارض مع Android API أو الكود الفعلي، لا تغيّر القرار بصمت. أثبت التعارض، حدّد الملف والسبب، ثم أوقف تلك الجزئية فقط إلى أن توجد معالجة متوافقة.

---

## 1. AAD للأدلة

### 1.1 Canonical AAD

اعتمد الإصدار:

```
AAD-v1
```

التمثيل الثنائي canonical هو:

```
versionByte
|| objectClassLengthUInt32BE
|| objectClassUtf8
|| evidenceIdLengthUInt32BE
|| evidenceIdUtf8
```

حيث:

- `versionByte = 0x01`
- `objectClass` = canonical lowercase UTF-8 string
- `evidenceId` = canonical UTF-8 string
- جميع حقول الأطوال: Big Endian

لا تستخدم:
- JSON
- concatenation بدون أطوال
- platform default charset
- locale-dependent conversion
- Base64 كبديل للـAAD
- String interpolation غير محددة

يجب أن يكون serialization deterministic 100%.

أي تغيير في هذه الصيغة مستقبلاً يتطلب إصدار AAD جديد.

**مثال مفاهيمي:**
```
0x01 || length(objectClass) || UTF8(objectClass) || length(evidenceId) || UTF8(evidenceId)
```

### 1.2 هل AAD إلزامي؟

**نعم.**

كل عملية تشفير وفك تشفير evidence يجب أن تستخدم AAD.

لا يوجد:
- encryption بدون AAD
- decrypt بدون AAD
- optional AAD
- fallback عند غياب AAD

إذا كان AAD مفقودًا أو غير قابل للبناء بشكل صحيح: **fail closed.**

### 1.3 evidenceId mismatch

**نعم.**

أي اختلاف بين evidenceId المتوقع والمستخدم لفك التشفير يجب أن يؤدي إلى:

```
EvidenceUnavailableException
```

ولا يجوز كشف تفاصيل إضافية للمستدعي عن:
- key state
- ciphertext validity
- authentication tag
- actual stored evidenceId
- سبب داخلي دقيق يمكن استخدامه لاختبار البيانات

سجّل فقط event-safe diagnostic مناسب دون تسريب بيانات حساسة.

### 1.4 AAD tests المطلوبة

أنشئ اختبارات تثبت:

1. نفس البيانات + نفس AAD = decrypt success.
2. تغيير version = decrypt failure.
3. تغيير objectClass = decrypt failure.
4. تغيير evidenceId = `EvidenceUnavailableException`.
5. تغيير طول أحد الحقول = failure.
6. تغيير ترتيب الحقول = failure.
7. UTF-8 deterministic.
8. empty/invalid identifiers handled safely.
9. no encryption path bypasses AAD.
10. no decrypt path bypasses AAD.

---

## 2. Android Keystore

### 2.1 Alias scope

النطاق المعتمد: **per application installation / Android application identity**

لا تجعل alias مرتبطًا بـlocal user.

استخدم alias ثابتًا versioned مثل:

```
shieldra_evidence_v1
```

Android Keystore نفسه يوفّر نطاقًا مرتبطًا بالتطبيق/UID؛ لا تنشئ installation identifier كبديل أمني للمفتاح.

### 2.2 User authentication / biometric

**لا.**

لا تجعل evidence encryption key الحالي يتطلب `setUserAuthenticationRequired(true)` ولا BiometricPrompt.

السبب: SHIELDRA يحتاج حماية تلقائية للأدلة الناتجة عن الأحداث الأمنية، بما في ذلك background processing.

المصادقة يمكن أن تستخدم لاحقًا لحماية:
- application access
- viewing sensitive evidence
- export
- administrative/destructive operations

### 2.3 StrongBox

مفضل عند توفره، وليس مطلوبًا.

الترتيب:
1. حاول StrongBox عندما يكون مدعومًا فعليًا.
2. إذا لم يتوفر، استخدم hardware-backed/software-backed حسب قدرات الجهاز.
3. لا تفشل التثبيت لأن StrongBox غير موجود.
4. ممنوع الادعاء أن SHIELDRA يستخدم StrongBox على كل الأجهزة.
5. يجب اكتشاف capability فعليًا.

### 2.4 Key invalidation / key loss

عند key invalidation أو key permanently unavailable:

```
BLOCKED + RECOVERY_REQUIRED
```

ممنوع إعادة إنشاء مفتاح جديد تلقائيًا.

السبب: إنشاء مفتاح جديد يجعل البيانات القديمة غير قابلة للفك، وقد يخفي فقدان المفتاح.

يجب أن تكون الحالة واضحة:
```
Evidence storage unavailable. Recovery required.
```

لا يجوز حذف evidence تلقائيًا بسبب فقد المفتاح.

### 2.5 Software-key fallback

**ممنوع.**

لا تستخدم SharedPreferences، DataStore، Room، plain file، hardcoded secret، أو generated software AES key كبديل لمفتاح Android Keystore.

---

## 3. Key Rotation

### 3.1 هل rotation مطلوب؟

**نعم — مطلوب كجزء من التصميم قبل production.**

الإصداران:
- `shieldra_evidence_v1`
- `shieldra_evidence_v2`

### 3.2 Metadata

كل evidence يجب أن يعرف:
- `keyVersion`
- `aadVersion`

### 3.3 Rotation algorithm

```
CREATE new key
        ↓
READ evidence metadata
        ↓
DECRYPT using old key
        ↓
VERIFY plaintext/integrity
        ↓
ENCRYPT using new key + new AAD metadata
        ↓
WRITE temporary file
        ↓
fsync/close
        ↓
atomic rename
        ↓
VERIFY new file
        ↓
MARK migrated
```

لا تحذف المفتاح القديم قبل التأكد من نجاح جميع الأدلة التابعة له.

### 3.4 Resume after interruption

Rotation يجب أن تكون resumable.

لكل evidence:
- `PENDING`
- `IN_PROGRESS`
- `VERIFIED`
- `FAILED`

بعد crash/restart:
- لا تبدأ من الصفر
- افحص الحالة وأكمل العناصر غير المكتملة
- الملفات المؤقتة لا تعتبر evidence صالحًا
- لا تستبدل evidence صالحًا بملف غير مكتمل

### 3.5 Rotation tests

اختبر:
- normal rotation
- interruption before encryption
- interruption after encryption
- interruption before rename
- interruption after rename
- corrupted old evidence
- corrupted new evidence
- duplicate retry
- already migrated evidence
- missing old key
- missing metadata
- mixed v1/v2 evidence

---

## 4. Room migrations

### 4.1 Current schema

Room version = **1** حاليًا.

لا تضف migration اصطناعية فقط لإظهار وجود migration.

### 4.2 Unsupported versions

أي version غير مدعوم:

```
RecoveryRequired
```

لا destructive fallback.

### 4.3 Migration failure

عند migration failure:

**حافظ على قاعدة البيانات.**

لا تستخدم:
- `fallbackToDestructiveMigration()`
- drop database
- recreate database
- حذف البيانات تلقائيًا

النتيجة: `RecoveryRequired` مع الاحتفاظ بقاعدة البيانات للتشخيص/الاسترداد.

### 4.4 Downgrade

عند downgrade غير مدعوم: `RecoveryRequired` ولا destructive downgrade.

### 4.5 First migration

لا توجد migration مطلوبة الآن من v1 → v2.

عند أول تغيير حقيقي في schema:
- version 2
- explicit Migration(1, 2)
- migration tests

لا يسمح بإخفاء schema change داخل destructive fallback.

### 4.6 Room tests

اختبر:
- opening v1 database
- supported schema
- unsupported future version
- downgrade
- migration failure
- no destructive fallback
- foreign keys
- event/evidence reference integrity

---

## 5. Evidence lifecycle

### 5.1 Room reference موجود، الملف غير موجود

النتيجة: **UNAVAILABLE**

لا تحذف Room reference تلقائيًا. الـreference يمثل حقيقة تاريخية.

### 5.2 ملف موجود بدون Room reference

النتيجة: **QUARANTINE**

ليس delete مباشر. ضع الملف في quarantine خارج evidence-active namespace ثم:
- سجّل سبب quarantine
- امنع عرضه كـvalid evidence
- امنع مشاركته/تصديره
- اجعل cleanup لاحقًا idempotent

### 5.3 Event deletion

اعتمد:
```
ACTIVE → DELETION_PENDING/TOMBSTONED → evidence cleanup → metadata cleanup → FINALIZED
```

الهدف: crash-safe, resumable, idempotent.

إذا فشلت عملية حذف evidence: لا تدّع أن event تم تنظيفه، احتفظ بحالة pending/recovery.

### 5.4 Retention

| النوع | المدة |
|-------|-------|
| Evidence files | 30 days |
| Security Event metadata/history | 90 days |
| Quarantined files | 7 days maximum |

لا يوجد permanent retention افتراضيًا.

يجب أن تكون retention policy centralized وقابلة للتعديل في policy layer.

### 5.5 Export

**Disabled by default.**

عند تنفيذ export مستقبلاً يجب أن يتطلب:
- explicit user action
- appropriate authentication
- redaction policy
- audit record
- secure temporary handling
- no accidental plaintext persistence

حتى ذلك الحين: `EXPORT_DISABLED` ولا يوجد export bypass مخفي.

### 5.6 Evidence deletion

الحذف اليدوي الصريح:
```
authenticated explicit user action → tombstone/deletion pending → secure cleanup → final state
```

لا يتم الحذف الصامت أثناء: app startup, migration, key initialization, failed decryption, missing reference, recovery.

### 5.7 Factory reset / app reset

عند تنفيذ reset security storage:
1. stop active evidence operations
2. mark deletion/recovery state
3. remove evidence files
4. remove Room event metadata
5. remove DataStore security settings where applicable
6. delete Keystore evidence key
7. verify storage absence
8. return clean state

يجب أن يكون reset: **explicit + authenticated + destructive confirmation**

لا يتم تلقائيًا عند أي exception.

---

## 6. Security failure semantics

| الحالة | النتيجة |
|--------|----------|
| Corruption | unavailable / quarantine |
| Missing evidence | unavailable |
| Missing key | BLOCKED / RECOVERY_REQUIRED |
| AAD mismatch | EvidenceUnavailableException |
| Migration error | RecoveryRequired |
| Unsupported DB | RecoveryRequired |
| Unknown state | fail closed |

ممنوع تحويل security/storage failure إلى:
- "create a new key"
- "delete the old data"
- "pretend evidence doesn't exist"
- "continue silently"

---

## 7. Mandatory implementation requirements

لكل قرار:
```
READ → TRACE USAGES → IMPLEMENT → STATIC REVIEW → UNIT TESTS → INSTRUMENTED TESTS
→ BUILD → ADVERSARIAL REVIEW → REGRESSION REVIEW → DIFF REVIEW
→ COMMIT → PUSH → VERIFY REMOTE → UPDATE ROADMAP → COMMIT ROADMAP → PUSH → VERIFY CI
```

---

## 8. لا تقم بإغلاق المرحلة بالتوثيق فقط

الـphase يغلق فقط عند وجود evidence حقيقي:
```
PASS = implementation + tests + build + verification + audit + remote CI evidence
```

---

## 9. CI Gate

بعد كل batch:
1. انتظر CI
2. افحص جميع jobs
3. لا تعتمد على overall status فقط
4. افحص failed/skipped/cancelled jobs
5. افحص logs عند الحاجة
6. تأكد أن Android tests/build نفذت فعليًا
7. لا تعتبر "in_progress" نجاحًا
8. لا تعتبر workflow existence دليلًا على نجاح الاختبارات

---

## 10. Current 4D-7 gate

قبل الانتقال إلى المرحلة التالية، تحقق من:
- Run 35886198586
- Run 35886254375

إذا failed: STOP → inspect → fix only required scope → test → commit → push → CI

إذا success: verify jobs → inspect artifacts → compare vs decisions → close 4D-7 → update ROADMAP

---

## 11. Full remaining SHIELDRA roadmap

- Phase A: Architecture integrity
- Phase B: Decision enforcement
- Phase C: Event pipeline
- Phase D: G4 local storage/security
- Phase E: Android capability verification
- Phase F: Lock Guard production adapter
- Phase G: Motion Guard
- Phase H: SIM/subscription Guard
- Phase I: Battery anomaly Guard (وفق السياسة المعتمدة فقط)
- Phase J: Panic Guard
- Phase K: Evidence capture
- Phase L: Alert/notification pipeline
- Phase M: Background monitoring
- Phase N: Local authentication/access control
- Phase O: History/dashboard production data
- Phase P: External-service interfaces
- Phase Q: Device/runtime verification
- Phase R: Regression/security audit
- Phase S: Release-readiness audit

لا تضف features خارج roadmap لمجرد أن تنفيذها ممكن.

---

## 12. Parallelization

مسموح بالتوازي فقط عندما لا توجد dependency:
- AAD tests + Room migration tests + documentation audit + static security scans (مثال مسموح)

إذا كانت المهام تعدّل نفس الملفات أو تعتمد على schema/key lifecycle: **SEQUENTIAL**

---

## 13. Mandatory self-adversarial review

قبل كل commit يجب أن يسأل AI GitHub نفسه:

- What did I assume?
- What did I not verify?
- What can fail after process death?
- What happens after a crash?
- What happens after duplicate execution?
- What happens if storage is corrupted?
- What happens if the key disappears?
- What happens if AAD is modified?
- What happens if Room and filesystem disagree?
- What happens after reinstall?
- What happens after downgrade?
- What happens after Android background restrictions?
- What happens when the device has no StrongBox?
- Did I accidentally create a software-key fallback?
- Did I accidentally introduce destructive recovery?
- Did I silently change an approved policy?
- Did I modify unrelated code?

---

## 14. No silent policy changes

ممنوع تغيير: AAD، key scope، authentication policy، StrongBox policy، key recovery policy، rotation policy، migration policy، retention، deletion semantics، evidence semantics بدون تحديث هذا المستند صراحة.

---

## 15. Final production gate

SHIELDRA لا يعتبر Production Security Ready إلا بعد:

```
ALL APPROVED DECISIONS IMPLEMENTED
+ ALL REQUIRED TESTS PASS
+ ANDROID BUILD PASS
+ INSTRUMENTED TESTS PASS
+ DEVICE VERIFICATION
+ BACKGROUND BEHAVIOR VERIFIED
+ STORAGE/CRYPTO AUDIT
+ GUARD AUDIT
+ EVIDENCE AUDIT
+ ALERT AUDIT
+ PERMISSION AUDIT
+ PRIVACY AUDIT
+ NO DESTRUCTIVE FALLBACK
+ NO SOFTWARE KEY FALLBACK
+ CI PASS
+ ROADMAP COMPLETE
+ FINAL SELF-ADVERSARIAL AUDIT
```

لا تستخدم عبارة "100% secure" ولا تدّعي حماية لا يمكن إثباتها على Android.

---

## 16. Mandatory reporting format

بعد كل batch:

```
BATCH:
SCOPE:
FILES CHANGED:
DECISIONS ENFORCED:
TESTS:
ANDROID TESTS:
BUILD:
STATIC AUDIT:
SECURITY AUDIT:
SELF-ADVERSARIAL FINDINGS:
COMMIT:
REMOTE:
CI:
ROADMAP:
STATUS:
BLOCKERS:
NEXT BATCH:
```

إذا لم يتم تنفيذ اختبار: **NOT RUN** (لا تقل PASS)

إذا كان CI IN PROGRESS: لا تغلق المرحلة.

---

## FINAL DIRECTIVE

- Repository الهدف: `toufikben/shieldra`
- لا تعدّل: `toufikben/Ai_super_cleaner`
- اعتبر هذا المستند Source of Truth
- ابدأ بالتحقق من الحالة الحالية والـCI أولًا
- أغلق 4D-7 إن كان مستحقًا
- نفّذ هذه القرارات على مراحل صغيرة قابلة للمراجعة
- لا تنتقل إلى feature جديدة قبل إغلاق gate السابقة
- لا تكتفِ بكتابة الكود: افحص، اختبر، ابنِ، راجع، ادفع، تحقق من GitHub، تحقق من CI، ثم حدّث Roadmap

**Received:** 2026-09-24
**Source:** Owner-supplied final decision document
**Authority level:** BINDING — supersedes any prior proposal on covered topics
