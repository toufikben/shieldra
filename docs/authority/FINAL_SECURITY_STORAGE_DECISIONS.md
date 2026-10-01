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
| Quarantined files | 30 days |

> **تعديل المالك 2026-09-28 (سجل التعديلات A-1):** الحد الأقصى 7 أيام
> لملفات quarantine أُلغي واستُبدل بمدة موحّدة 30 يومًا. انظر §17.

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

> **تعديل المالك 2026-09-28 (سجل التعديلات A-2 و A-3):**
> - **(A-2)** خطوة 6 `delete Keystore evidence key` مُلزِمة: إعادة الضبط تحذف
>   مفتاح evidence نهائيًا. هذه هي **الحالة الوحيدة** المسموح بها في SHIELDRA
>   التي تُلغي فيها مفاتيح Keystore؛ §3 و §2.5 يبقمان ساريين خارجها.
> - **(A-3)** ترتيب الخطوات 3–6 يتقدّم على الخطوة 7. معيار النجاح هو **إثبات غياب
>   التخزين**، وأي فشل في التنظيف يُسجَّل كـtombstone ولا يُعلَن نجاح. هذا
>   استثناء مُوثَّق وصريح لـ§4.3: قاعدة "الاحتفاظ بالـDB عند فشل الترحيل"
>   تخصّ **الترحيل (migration)** ولا تخصّ **إعادة الضبط الصريحة المدعومة
>   بتأكيد إتلاف**. إعادة الضبط لا تُستخدم كمسار استرجاع.
> انظر §17.

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

## 17. سجل تعديلات المالك (Amendment log)

التزامًا بـ§14، أي تعديل لقرار مُعتمد يُسجَّل هنا بتاريخه وسببه ونصه قبل
التنفيذ. الرقم الأصلي يبقى ظاهرًا في القسم المعدَّل مع إشارة إلى رقم التعديل.

| المعرّف | التاريخ | القسم | القرار المُعدَّل | النص الجديد | السبب |
|---------|---------|-------|----------------|-----------|-------|
| A-1 | 2026-09-28 | §5.4 | `Quarantined files: 7 days maximum` | `Quarantined files: 30 days` — مدة موحّدة مع الأدلة العادية، وسقف الـ7 أيام مُلغى | قرار المالك: "اعتمد 30 يوما للأدلة المعزولة" |
| A-2 | 2026-09-28 | §5.7 | §3 + §2.5 (منع حذف المفاتيح) | إعادة الضبط الصريحة تحذف مفتاح evidence نهائيًا. الاستثناء الوحيد المسموح في SHIELDRA لحذف مفاتيح Keystore | قرار المالك: "إعادة ضبط المصنع تحذفها نهائيا" — الخطوة 6 في §5.7 قائمة أصلًا؛ أُكِّد أنها مُلزِمة ولا تُهمَل |
| A-3 | 2026-09-28 | §5.7 خطوة 7 | §4.3 (الاحتفاظ بالـDB عند فشل الترحيل) | ترتيب الحذف يسبق التحقق؛ الفشل يُسجَّل كـtombstone ولا يُعلَن نجاح. استثناء صريح: §4.3 تخصّ الترحيل، لا إعادة الضبط الصريحة | قرار المالك: "الإزالة تتقدّم، الفشل يُوثَّق" |
| A-4 | 2026-09-29 | §6 (AAD_MISMATCH/MISSING_KEY) + §5.7 | §6 (MISSING_KEY → RECOVERY_REQUIRED) مقابل A-2 (بعد إعادة الضبط المفتاح مفقود نهائيًا) | تعارض ظاهري أُ resolve بقرار المالك "استرجاع عند غياب غير متعمّد": غياب المفتاح أثناء القراءة مع بقاء الملف = RECOVERY_REQUIRED (يجب تدخّل المشغِّل). أما بعد إعادة ضبط مؤكَّدة (A-2) فالحذف أزال الأدلة نفسها، فتبقى القراءة UNAVAILABLE نهائيًا. الآلية طبيعية: المسار يفرّق بحسب وجود ciphertext، لا بحسب حالة المفتاح فقط | قرار المالك: "استرجاع عند غياب غير متعمّد" |
| A-5 | 2026-09-30 | §15 | بوابة التشغيل `StorageRuntimeApproval.approved = false` (الافتراض قبل هذا التعديل) | `StorageRuntimeApproval.approved = true` — التطبيق المعتمد يفتح التخزين المحلي عند بدء العملية عبر `openOrRecovery` (فشل الفتح يبقى RecoveryRequired، §4.2–§4.4) | إجابة المالك Q-C2 (الخيار 1) + برووتوكول §11/§14: القلب في نفس الدفعة المرتبطة بأول كاشف حقيقي (متحكم الذعر — الدفعة C) وبعد نجاح تحقق الجهاز (OK، 46 اختبارًا على RMX3910). القلب تم في commit ظاهر؛ وأي إرجاع إلى `false` يستوجب تعديلًا مسجلًا جديدًا هنا |

| A-6 | 2026-09-30 | §16 + §23 + §31 (الدفعة E) | لا يوجد إذن `POST_NOTIFICATIONS` ولا كود إشعارات؛ بوابة `phase3-review-check.sh` تفشل أي `<uses-permission>`؛ ووصف المتجر يَعِد بـ "zero permissions" | **إشعارات محلية حقيقية فقط**، وعلى Android 13+ يُضاف `android.permission.POST_NOTIFICATIONS`. الطلب **ليس** عند لحظة Panic، بل في سياق إعداد واضح مع شرح سبب الحاجة قبل الطلب. **رفض الإذن لا يمنع تسجيل Security Event**: يبقى Panic صالحًا ومحفوظًا. تُفحص حالة الإذن فعليًا قبل كل محاولة. لا يُسجَّل `SENT`/`DELIVERED` لمجرد استدعاء واجهة Android — تُفصل ثلاثة أشياء: حالة الإذن / محاولة الإشعار / نتيجة النشر. لا يُدَّعى أن المستخدم رأى الإشعار أو أنه وصل جهازًا آخر. **نطاق مقصور**: لا FCM ولا Firebase ولا سحابة ولا SMS ولا بريد ولا مزوّد خارجي (مؤجَّل). تُصحَّح صياغة مواضع وعد "zero permissions" فقط، دون إعادة كتابة وصف المتجر | قرار المالك A-6 "Real Local Notifications": الإشعار صار جزءًا من الوظيفة الفعلية، فلا يمكن تنفيذ §16 بدون إذن؛ و§23 (كل إذن يُطلب من الميزة التي تحتاجه عند حاجتها) هو القيد الملزم، فوعد "zero permissions" نسبي لا مطلق |
| A-7 | 2026-09-30 | §12–§13 (الدفعة E) | عدّاد تفعيلات الذعر `activationCount` داخل المنسّق فقط (ConcurrentHashMap)؛ لا يُحفظ في القاعدة، وQ-C9 مفتوح | `activationCount` يصبح **metadata دائمة داخل نفس Panic Event** تُخزَّن مع الحدث وفق نموذج Room القائم، ويُحفظ `lastActivatedAt` إن أمكن بنظافة. **ممنوع**: عدّاد عام دائم، أو استخدام العدّاد لإنشاء `EventId` آخر، أو رفع الخطورة، أو تجاوز دورة الحياة، أو نافذة زمنية، أو تهدئة Panic. **دورة الحياة هي المرجع الوحيد**: بعد وصول الحدث النهائي، ضغطة Panic جديدة = `EventId` جديد بـ `activationCount=1`. التكرار والتفعيل المتزامن يظلان غير مكررين، ولا يُعرَّف "نفس الذعر" زمنيًا | قرار المالك A-7: "اعتمد `activationCount` كـ persistent metadata داخل نفس Panic Event"، مع صريح منع أي تفسير يولّد حدثًا ثانيًا أو يرقّي الخطورة |
| A-8 | 2026-09-30 | §17 (الدفعة G) | اقتراح الدفعة F: "إضافة Foreground Service + `specialUse` + إذنَين" قُبل كما لو كان مطلوبًا مطلقًا الآن | **معتمد كمبدأ معماري، لا ككود أذونات يُضاف الآن.** لا يُضاف `<service>` ولا `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_SPECIAL_USE` إلا عندما توجد **خدمة مراقبة حقيقية يستعملها المستخدم فعليًا**: يبدأها هو (user-initiated)، يمكنه إيقافها، والوظيفة أساسية ومفيدة وواضحة له. `specialUse` يُختار **فقط** إذا لم يوجد نوع FGS أكثر تحديدًا يناسب الكاشف الحقيقي، مع `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` وبيان مراجعة Google Play. **ممنوع منعًا باتًا**: إنشاء Service فارغة أو إضافة إذن "لمجرد كسر بوابة CI" أو لإرضاء فحص ثابت | قرار المالك 2026-09-30: "لا نضيفه لمجرد إرضاء بوابة CI. يُضاف عندما يكون هناك Monitoring Service حقيقي ومستخدم فعليًا" و"A-8 = APPROVED AS AN ARCHITECTURAL DECISION, NOT AS PERMISSION-ONLY CODE". البوابات الحالية (`g4b-review-check.sh`: `<service>` = فشل، `FOREGROUND_SERVICE` = فشل، إذن واحد بالضبط) تظل سارية كما هي ولا تُرخى استباقًا |
| A-9 | 2026-09-30 | §17 + §31 (الدفعة G) | صياغة مقترحة للإشعار الدائم: "جهازك مُراقَب" | **موافق**: عندما تكون المراقبة تعمل فعلًا عبر FGS يجب أن يكون وجودها واضحًا للمستخدم بإشعار **مستمر ودقيق**. الصياغة المعتمدة: `SHIELDRA protection is active` (أو ما يعادلها بالعربية)، ويُمنع: "حمايتك مضمونة 24/7" أو أي صيغة ضمان دائم، وأي وصف لحالة غير موجودة | قرار المالك 2026-09-30: "طالما FGS يعمل، يجب أن يكون وجوده للمستخدم واضحًا بإشعار مستمر ودقيق. لا نكتب ادعاء مراقبة 24/7" — وهو متوافق مع §31 ومع منع ضمان 24/7 المقرَّر أصلًا في §17 |
| A-10 | 2026-09-30 | §17 + §23 (الدفعة G) | تشغيل تلقائي بعد إعادة التشغيل (`RECEIVE_BOOT_COMPLETED` + `BOOT_COMPLETED`) | **مرفوض في هذه المرحلة.** لا `RECEIVE_BOOT_COMPLETED` ولا `BOOT_COMPLETED` الآن. المسار الذي يُبنى ويُختبر أولًا: فتح التطبيق → المستخدم يفعّل الحماية → تبدأ المراقبة. التشغيل التلقائي بعد الإقلاع مؤجَّل إلى ما بعد اختبار FGS الفعلي وسلوك OEM على جهاز المالك. ملاحظة المالك: Android 15 قيَّد بدء بعض أنواع FGS من `BOOT_COMPLETED` وبعضها مستثنى (منها `specialUse`)، لكن ذلك **لا** يعني بناء التشغيل التلقائي الآن | قرار المالك 2026-09-30: "أرفضه في هذه المرحلة… نختبر أولًا App opened → user enables protection → FGS starts → monitoring ثم نختبر سلوك الجهاز الفعلي" |
| A-11 | 2026-09-30 | §31 + §17 (الدفعة G) | صيغة مقترحة: "بدون تعطيل تحسين البطارية سيقتل النظام الـ FGS غالبًا" | تصبح **توصية اختيارية** لا شرطًا ولا ضمانًا: *"For more reliable background protection on some devices, SHIELDRA recommends allowing unrestricted battery usage. Your device manufacturer may still apply additional background restrictions."* ويُمنع صيغة "عطّل التحسين وإلا لن يعمل SHIELDRA"، ويُمنع الادعاء الحتمي أن الخدمة ستُقتل بدونه. **التعليل الصحيح المعتمد**: Android يسمح للمصنّعين بقيود مختلفة على سلوك الخلفية، ووضع التطبيق في Restricted قد يمنع عمليات خلفية معيّنة؛ وتعطيل تحسين البطارية هو أحد استثناءات **قيود بدء FGS من الخلفية** وليس ضمانًا لعدم التوقف | قرار المالك 2026-09-30: "Battery Optimization = Optional Protection Recommendation… وليس شرطًا إجباريًا ولا ادّعاءً أن FGS سيُقتل حتمًا بدونه. وهذا مهم جدًا للصدق في §31" |
| A-12 | 2026-10-01 | §17 + §18 (الدفعة G، شريحة G3) | صيغة مقترحة بعد نجاح G2: "كاشف الحركة يعمل"، وجعل 106 Hz و≈0.52 m/s² قيمًا ثابتة في الكود، وتسجيل الحساسَين (المُطلَق أحادي الإطلاق والمستمر) في طبقة واحدة | **نطاق G3 = محوّل حساس الحركة + طبقة القياس/الجودة فقط.** المخرجات **بيانات إشارة فقط**: متجه التسارع، المقدار، الطوابع الزمنية الحقيقية، معدل العينات المحسوب من تلك الطوابع، والجودة المحسوبة من البيانات الخام، وتيار داخلي نظيف للطبقة التالية. **ممنوع في G3 (قائمة المالك الحرفية)**: Motion Security Event، خطورة HIGH، Panic، إشعار، كتابة Room، أدلة، كاميرا، موقع، SMS/بريد/سحابة، cooldown، عتبة بمعنى «X m/s² = هجوم»، اعتبار 106 Hz شرطًا، تشغيل FGS لمجرد اختبار المحوّل، و`specialUse` بلا خدمة مراقبة حقيقية. **قواعد ملزِمة**: (1) مُطلَق `SIGNIFICANT_MOTION` أحادي الإطلاق لا يُمزج مع الحساس المستمر في طبقة واحدة؛ G3 يفحص توافر الاثنين ويسجّل accelerometer فقط عبر `registerListener`؛ (2) **106 Hz نتيجة اختبار لا عقد**: Android يعتبر مدة الإبلاغ المطلوبة اقتراحًا ويطبّق سقفًا عامًّا (~200 Hz) على أهداف API 31+؛ (3) **لا تُثبَّت الأرقام المقاسة** (لا 106 Hz ولا 0.52 m/s² ولا سقف الضجيج) كثوابت في الكود؛ تُمرَّر القيم محقونة ويُقاس المعدل من الطوابع الفعلية؛ (4) **Signal ≠ Confirmed Security Event**: التحقق والتأكيد والخطورة لطبقات لاحقة؛ (5) عبارة الإغلاق المعتمدة حرفيًا: *"The Android sensor path is available and produces usable accelerometer samples on the tested device; G3 does not yet establish motion-detection accuracy or security-event correctness."* ولا تُستبدل بادعاء دقة الكشف | قرار المالك (G3 scope ruling 2026-10-01): «نعم: إنشاء Android MotionSensorAdapter… استخدام timestamps الحقيقية لحساب معدل العينات، وليس افتراض أن 106 Hz ثابت… حساب signal quality من البيانات الخام» + قائمة ❌ الصريحة + رفضه المسبق لأي مبالغة في نتيجة G2 |
| A-13 | 2026-10-01 | §17 + §18 (الدفعة G، شريحة G4-A) | ما كان سيُبنى بلا هذا التعديل: استنتاج معمارية FGS من قياس، أو إضافة خدمة/إذن/wake lock «لكي ينجح الاختبار»، أو تحويل القياس إلى كاشف حركة أو حدث أمني | **G4-A = حملة قياس فقط (measurement-only gate).** السؤال الوحيد المطروح: هل يستمر accelerometer في توصيل عينيات إلى **عملية الاختبار** بينما الجهاز **ساكن فعليًا** والشاشة **مطفأة**؟ أطوار ملزمة بالترتيب: A أساس (60 ث، شاشة عامّة) · B (60 ث، شاشة مطفأة) · C (5 د، شاشة مطفأة) · D (15 د، شاشة مطفأة)، **ثلاثة تكرارات لكل طور = 12 تشغيلًا** على RMX3910، بنفس APK ونفس التثبيت، بلا تحريك مقصود ولا لمس ولا تغيّر شحن أثناء التشغيل، والعودة للشاشة بعد اكتمال النافذة لا قبلها. **ممنوع في G4-A**: مسار مراقبة إنتاجي، `<service>` أو أي FGS أو إضافته لتنجح التجربة، `FOREGROUND_SERVICE_SPECIAL_USE`، `RECEIVE_BOOT_COMPLETED`، إعفاء تحسين البطارية، **`PARTIAL_WAKE_LOCK` في القياس الأولي** (يُوقف العمل ويُطلب قرار عند الغموض بدل إضافة صامتة)، إشعار، إذن إنتاجي جديد، سطح إنتاجي، أو أي تليين لبوابة G4-B. **ممنوع ادّعاء**: «24/7» أو «المراقبة المستمرة ممكنة» أو دقة كشف الحركة أو صحة حدث أمني. **قيد التفسير الحرفي**: اختبار instrumented يُبقي العملية حيّة ويمنحها أولوية اختبار، فلا يُكافئ عملية خلفية إنتاجية تحت Doze/App Standby/قيود OEM؛ لذلك **نتيجة «استمرار التوصيل» لا تثبت أن FGS غير ضروري، ونتيجة «الانقطاع» لا تثبت أن `specialUse` مطلوب** — كلتاهما دليلٌ لتحقيق لاحق في Android/OEM/FGS. العبارة الختامية المعتمدة عند استمرار التوصيل حرفيًا: *"The tested Android sensor path continued to deliver accelerometer samples to the instrumented test process during the measured screen-off interval on RMX3910. This does not establish production background survivability under Doze, App Standby, or OEM restrictions, and does not establish whether a Foreground Service is required or unnecessary for SHIELDRA."* | قرار المالك (G4-A brief 2026-10-01): «حملة قياس فقط… لا نضيف FGS لكي ينجح الاختبار… لا PARTIAL_WAKE_LOCK في التجربة الأساسية… لا أريد أن يتحول نجاح G4 إلى "الحساس يعمل في الخلفية إذن لا نحتاج FGS" ولا إلى "توقف إذن specialUse مؤكد"… ولا أريد أي تعديل على app/src/main في هذه الخطوة» + ترتيبه: G4-A ثم تحليل ثم قرار FGS ثم قياس نصف Q-G1 القوي |

| A-14 | 2026-10-01 | §17–§18 (شريحة G4-A) + ترتيب الدفعة H | ما كان سيحدث بلا هذا التعديل: خلط جولات القياس بجولات البنية التحتية في جدول استنتاج واحد، واعتبار تشغيل ملوّث دليلاً، والقفز من إغلاق G4-A مباشرة إلى كود H على أساس «المسار مفتوح»، واختراع agent لسياسات التقاط والخصوصية لم يقرّرها المالك | **سلّتان إلزاميتان في تقرير G4-A، لا جدول واحد.** (1) **أدلة صالحة**: ما يرجع بأدلة من المركَبة ويُقبل بتصنيف A-13 (`CONTINUING`/`INTERRUPTED` مع التلويث معلنًا). (2) **فشل بنية تحتية — لا يدخل الاستنتاج**: انقطاع USB/ADB، `NO_EVIDENCE`، `TIMEOUT_NO_DATA`، وأي `CONTAMINATED`. القواعد: **لا يُعاد أي تشغيل فشل سببه البنية التحتية لمجرد إكمال العدد** — A-1/A-2 (2026-10-01) سببهما انقطاع النقل لا قياس الحساس، وأساس الشاشة المضاءة مغطّى بـA×3 النظيفة من `campaign-1`، فيُذكر سبب الاستبعاد ويُترك الصف كما هو؛ و**أي تشغيل ملوّث لا يُستعمل في الاستنتاج ويُعاد مرة واحدة على الأقل في ظروف نظيفة** — B-1 الملوّثة بـ`screen_was_interactive_in_1_of_3_polls` تُعاد. **التسلسل الملزِم بعد القياس**: تقرير G4-A النهائي ← مراجعة الأدلة ← قرار A-8/FGS (قرار المالك وحده) ← ثم حزمة الدفعة H **للقرارات فقط**. **ممنوع على agent اختراع قرارات H**: مدة التقاط الكاميرا، عدد الصور، دقة الموقع، مدة احتفاظ الموقع، وما إذا كانت الكاميرا مسموحة أصلًا (Q-C3/Q-C5). **لا كود H قبل تعديل السلطة والأحكام نفسها**: `group-c-contract-check.sh` («No camera, location collection, or network implementation») و`g4b-review-check.sh` (إذن `<uses-permission>` واحد بالضبط، `<service>` = فشل) يفشلان أي تنفيذ كاميرا/موقع، فأي كود H يستلزم قرار سلطة صريحًا يعدّل البوابات والأحكام أولًا، ولا تُرخى البوابات ضمنيًا لتمرير كود. **سؤال التقرير الوحيد**: «ماذا حدث فعليًا للمسار الحسّي بينما الشاشة مطفأة؟» وعبارة «FGS مطلوب» ليست نتيجة للحملة بل قرار معماري لاحق | قرار المالك الحرفي (2026-10-01): «لا نعتبر B-1 صالحًا… يجب إعادة B-1 مرة واحدة على الأقل في ظروف نظيفة، ولا نستخدم قراءتها في الاستنتاج النهائي» · «A-1/A-2 لا ينبغي إعادة تشغيلهما لمجرد إكمال رقم 3؛ سبب الفشل كان انقطاع USB/ADB وليس قياس الحساس» · «لا تبدأ H بعد بمجرد انتهاء الجهاز… G4-A النهائي ← مراجعة الأدلة ← قرار A-8/FGS ← ثم حزمة H للقرارات فقط» · «لا يجوز للـAI اختراع: مدة التقاط الكاميرا، عدد الصور، دقة الموقع، احتفاظ الموقع، السماح بالكاميرا أصلًا» · «تعارض بوابات `group-c-contract-check` و`g4b` مع إدخال Camera/Location يعني أنه لا ينبغي أن يبدأ H-code قبل تعديل السلطة/الأحكام نفسها» · «التقرير يفصل Valid evidence عن Invalid / infrastructure failures» ويستخرج فقط ماذا حدث للمسار الحسّي، لا «FGS مطلوب» |

| A-15 | 2026-10-01 | §17 + §18 (الدفعة G، قرار Q-G3/A-8) | A-8 أبقت التوقيت مفتوحًا («يُضاف عندما توجد خدمة مراقبة حقيقية») ولم تحسم أيُعتمد مسار الـFGS أصلًا؛ ما كان سيحدث بلا هذا التعديل: بقاء المعمارية معلقة بلا قرار، أو تحويل القرار إلى أذونات وخدمة فارغة قبل اعتماد عتبة Q-G1، أو اعتبار أن G4-A أثبت تقنيًا أن الـFGS ضروري | **مراقبة الحركة المستمرة تعمل عبر Foreground Service حقيقي.** القيود الملزِمة: (1) تبدأ الخدمة **بفعل ظاهر يبدأه المستخدم** داخل التطبيق — لا بدء صامت ولا بدء من خلفية؛ (2) **يستطيع المستخدم إيقافها**؛ (3) أثناء عملها الحقيقي تعرض **إشعارًا مستمرًا دقيقًا** يصف ما يحدث فعلًا (صياغة A-9 المعتمدة `SHIELDRA protection is active`)؛ (4) **ممنوع** أي ادعاء حماية مضمونة 24/7 أو وصف حالة غير موجودة؛ (5) إرشاد تحسين البطارية يبقى **اختياريًا** لا شرطًا ولا ضمانًا (A-11 كما هو)؛ (6) **لا إقلاع تلقائي** في هذه الدفعة (A-10 كما هو)؛ (7) `specialUse` **ليس قرارًا مسبقًا**: يُختار فقط إذا أكدت مراجعة المستودع وواجهات API أنه لا يوجد نوع FGS أكثر تحديدًا يناسب حالة مراقبة الحركة الفعلية، ويبقى `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` وبيان مراجعة Google Play ملزمين عند اختياره؛ (8) `FOREGROUND_SERVICE` و`FOREGROUND_SERVICE_SPECIAL_USE` **مأذونان فقط** لتنفيذ الخدمة الحقيقية، لا تجهيزًا مسبقًا ولا لإرضاء بوابة؛ (9) **ممنوع منعًا باتًا** `Service` فارغة أو معدومة الاستدعاء أو تعديل أذونات/بوابات فقط، ويظل حكم A-8 ساريًا حرفًا: لا خدمة لمجرد كسر بوابة CI. **هذا السجل لا يصرّح بأي كود خدمة الآن**: التنفيذ مشروط باعتماد المالك لعتبة Q-G1 أولًا، والترتيب الملزِم مذكور في ملاحظات التقييد أدناه | قرار المالك الحرفي (2026-10-01): «Q-G3 = الخيار 1 — اعتمد معمارية Foreground Service حقيقية للمراقبة المستمرة للحركة» · «هذا **لا** يعني أن G4-A أثبت أن FGS ضروري تقنيًا؛ المالك يختار آلية تنفيذ مراقبة حقيقية ظاهرة للمستخدم عوض الاعتماد على عملية مُختبَرة/m.instrumented» · «سجّل هذا كـ**Amendment A-15** قبل تغيير المعمارية الإنتاجية» · «A-15 سجلُ قرار أولًا، ليس إذنًا بتنفيذ الخدمة كاملة فورًا» |

| A-16 | 2026-10-01 | §17 + §18 + §37 (الدفعة G، شريحة G5/الـFGS، قرار Q-G1) | A-15 علّق كود الخدمة على «اعتماد المالك لعتبة Q-G1»، فبلا هذا السجل كان أحد أمرين occurs: إمّا أن يسقط المنع ضمنيًا لأن نصف السؤال صار مُجابًا، فيُكتب كود خدمة بلا سجل، أو يبقى مانعًا لقرار اتخذه المالك فعلًا. كذلك: رقم 4.6 كان سيبقى في رسالة لا في سجل، وكان قياس حمل طبيعي رجّع `deliberate_movement_identified=false` سيُقرأ كأنه عيب في قاعدة المنتج فيُخفَّض «3 ثوانٍ» لإرضاء مؤشَر قياس، وكان نصف Q-G1 الآخر (ما يُعدّ ضجيجًا) سيُخترع له رقم | **نصف Q-G1 «الحركة القوية» مُجاب ومعتمد: `strongDeltaMs2 = 4.6 m/s²`** — قيمة معايرة معتمدة لشريحة SHIELDRA هذه، مبنية حرفيًا على قياس RMX3910: سكون `max 0.5192`، حمل طبيعي `p99 3.4878` و`max 5.1399`، وضربة ساكنة منفردة سابقة `4.5576` أثناء هاتف موضوع دون لمس؛ فاختيار 4.6 بدل 4.0 يتجاوز أعلى transient ساكن مقيس ويتجاوز p99 للحمل الطبيعي دون أن يقع فوق كامل نطاق الحركة الطبيعية. **قاعدة G1-02 لا تتغيّر:** ≈3 ثوانٍ من الحركة القوية المتصلة **أو** حالتان suspicious خلال 10 ثوانٍ، مع فحص جودة الإشارة أولًا وتجاهل isolated spikes، لأن **الزمن/التكرار جزء أساسي من الحماية من false positives وليس العتبة وحدها**. **وعدم اكتشاف الـharness للحمل الطبيعي ليس سببًا لتغيير قاعدة المنتج**: الـharness استعمل معيارًا مختلفًا (3× أعلى قراءة سكون، أي 1.5577) عن معيار الحارس نفسه، فلا تُخفَّض الـ3 ثوانٍ لينجح ذلك المؤشَر. **`MotionCalibration` تبقى قابلة للحقن** و4.6 ليست ادعاءً بأن كل هاتف Android له الحساسية نفسها. **شرط A-8/A-15(7) عن نوع الـFGS يبقى قائمًا حرفيًا**: لا يُختار `specialUse` بالاسم قبل مراجعة أنواع FGS الرسمية، والمراجعة مُسجَّلة في `docs/reports/batch-G5-foreground-service-type-review.md` ونتيجتها معروضة على المالك، لا مفروضة | قرار المالك الحرفي (2026-10-01): «Strong-motion threshold: **4.6 m/s²**» · «production default: now approved» · «**لا أغيّر قاعدة G1-02**» · «الزمن/التكرار هو جزء أساسي من الحماية من false positives، وليس threshold وحده» · «عدم اكتشاف الـharness للحمل الطبيعي لا يكفي لتغيير قاعدة المنتج؛ لأن الـharness استخدم معيارًا مختلفًا عن قاعدة الحارس نفسها. لا أريد تخفيض 3 ثوانٍ فقط لكي ينجح هذا المؤشر» · «وأبقي `MotionCalibration` قابلة للحقن، لكن 4.6 تصبح قيمة معايرة SHIELDRA المعتمدة لهذه الشريحة، وليست ادعاءً بأن كل هاتف Android له نفس الحساسية» · «**لا نختار `specialUse` بالاسم قبل مراجعة نوع الخدمة الفعلي**» · «أول خطوة داخله: API/type review ← Service ← user start/stop ← ongoing notification ← ربط Motion detector» |

| A-17 | 2026-10-01 | §17 + §18 + §23 (الدفعة G، إغلاق Q-G1 وتأكيد نوع الـFGS) | ما كان سيحدث بلا هذا التعديل: يبقى نصف Q-G1 الثاني («ما يُعدّ ضجيجًا») مفتوحًا إلى الأبد أو يُخترع له رقم؛ وكان رقم 250 ms المقترح من agent يمكن أن يُتبنى ضمنيًا بينما المالك يريد 100؛ وكان `minEpisodeMs` سيُقرأ كقاعدة تأكيد ثانية فتُراكَم فوق الـ3 ثوانٍ؛ وكان `specialUse` يبقى «مُراجَعًا غير معتمد» فيُكتب كود خدمة بلا قرار؛ وكان خطأ صياغة في تقرير المراجعة (أن `health` لا يُفتح إلا عبر `ACTIVITY_RECOGNITION`) سيتحوّل إلى causa معتمدة | **Q-G1 مُغلق بالكامل، والـFGS معتمد النوع.** القيم الخمس المعتمدة حرفيًا: `strongDeltaMs2 = 4.6` (A-16) · `noiseFloorDeltaMs2 = 0.8` · `minEpisodeMs = 500` · `maxSampleGapMs = 100` (**رُفض 250**: أطول فجوة مقيسة 10 ms عند وسيط 9.0 ms، والنافذة المرجعية الأبطأ وسيطها 19.0 ms، فـ100 ms تعطي هامشًا ≈10× أسوأ فجوة دون ربط حركتين منفصلتين داخل حدث واحد) · `requestedReportPeriodUs = 20_000`. **وفصل ملزِم لا يُخلط:** `minEpisodeMs` = **تعريف/تجميع episode** (متى تُحسب حلقة حركة أصلًا)، وقاعدة **G1-02** (≈3 s متصلة أو حالتان في 10 s) = **سياسة تأكيد** في `ApprovedGuardRules`؛ لا يجوز لأي كود أن يخفّض الثانية لأن الأولى موجودة، ولا أن يستعمل الأولى كبديل عنها. **نوع الخدمة معتمد: `specialUse`** مع `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` + `android:foregroundServiceType="specialUse"` + `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` وبيان مراجعة Google Play، بعد أن أسقطت المراجعة الأنواع الأربعة عشر واحدًا بواحد. **تصحيح المالك المقبول للتقرير**: لـ`health` مسار تقني أيضًا عبر `HIGH_SAMPLING_RATE_SENSORS` (لا عبر `ACTIVITY_RECOGNITION` وحده)، وسبب استبعاده هو **غرضه الموثّق (لياقة/صحة)** لا شكل الإذن — ولا يُختار نوع FGS بمقارنة الأذونات بينما تُخطئ في وصف حالة الاستخدام (روح A-8). **هذه الخمس هي كل معايرة الحركة الإنتاجية**: لا يُدخل مهندس رقمًا سادسًا ولا يغيّر واحدًا إلا بتعديل جديد هنا | قرار المالك الحرفي (2026-10-01): «**Q-G1b: 0.8 / 500 / 100 / 20000 — specialUse مؤكد**» · «**لا أعتمد `250 ms`**… 100 ms يعطي هامشًا كبيرًا دون السماح بربط فترات منفصلة بفجوة 250 ms داخل حدث أمني» · «`4.6 m/s²` يبقى العتبة المعتمدة التي سجّلها A-16» · «وتبقى قاعدة التأكيد كما هي» · «لا أريد من الـAI أن يخلط `minEpisodeMs = 500` مع قاعدة التأكيد البالغة ~3 ثوانٍ؛ الأولى **تعريف/تجميع episode**، والثانية **confirmation policy**» · «أعتمد `specialUse` لهذا الاستخدام، مع تصحيح صغير… `health` له أيضًا مسار تقني عبر `HIGH_SAMPLING_RATE_SENSORS`» |

| A-18 | 2026-10-01 | §17 + §18 + §23 (الدفعة G، شريحة G5: اعتماد تصميم الخدمة + إجابات Q-G5-a/b/c) | ما كان سيحدث بلا هذا التعديل: رقم `stallToleranceFactor` كان سيخترعه agent لأنه ليس ضمن الخمسة المعتمدة، وكان رفض `POST_NOTIFICATIONS` سيُبقي المراقبة تعمل بلا الإشعار الذي يوعدها A-15(3) أو كان سيُعلَّل بأكاذيب عن قيود Android، وكان ربط تكرار الحركة سيستعمل «أول حدث غير نهائي» المطلق فيرتبط تأكيدُ Motion بحدث Lock أو SIM نشط (وهو الخطر الذي نوقش أصلًا في `PanicIncidentCoordinator`)، وكان تصميم الـadapter يبقى بمالكين (`Service` و`MotionRuntime`) فتتضارب `isReporting`، وكان القياس البطاري سيُستعمل ذريعة لتعليق صحة G5 | **تصميم G5 معتمد بهذه الصيغة، والأساس الثلاثي مُجاب:** (1) **`stallToleranceFactor = 11.0`** — مُشتق من المسار المقاس لا مُختار: عند وسيط ≈9 ms يعطي ≈99 ms فيُطابق سقف `maxSampleGapMs = 100`؛ وإذا ارتفع الوسيط على الجهاز يبقى **السقف المطلق 100 ms هو الحاكم**؛ و`requestedReportPeriodUs = 20_000` كما اعتمده A-17. **الأرقام الستة هي كامل معايرة الحركة**: لا رقم سابع إلا بتعديل جديد هنا. (2) **fail closed عند فقدان رؤية الإشعار**: `Android < 33 → NotRequired → مسموح` · `≥ 33 → Granted مسموح / Denied لا تبدأ المراقبة / NotRequested → توجيه إلى طلب الإذن` مع سبب واضح. **السبب منتَجِي لا ادعاء تقني**: Android يسمح باستمرار الـFGS رغم رفض `POST_NOTIFICATIONS`، لكن إشعاره لا يظهر في الدرج، وA-15(3) يشرط مراقبة **ظاهرة** بإشعار مستمر دقيق — **فلا نبدأ مراقبة ونترك المستخدم بلا الإشعار الموعود**. الرفض **لا يمسّ مسار Panic** (A-6: رفض الإذن لا يمنع تسجيل Security Event). (3) **same-event deduplication بواجهة الحركة نفسها**: يُعاد استعمال `EventId` **الحدث غير النهائي من نوع MOTION فقط**، ولا يرتبط تأكيد Motion بأي حدث غير نهائي لغيره (Lock/SIM/Battery/Panic)؛ وبعد حدث نهائي → `EventId` جديد. **بلا cooldown جديد وبلا أي رقم جديد**. **ثلاثة تصحيحات معمارية ملزِمة على التصميم**: (أ) `MotionMonitoringService` **هو** مالك `MotionSensorAdapter`، و`MotionRuntime` يملك حالة/تحكّم المراقبة فقط **ولا يملك نسخة ثانية** من المحوّل (لا تضارب `isReporting` ولا unregister مزدوج)؛ (ب) **مصدر الحقيقة = حالة الخدمة الفعلية + `adapter.isReporting` الفعلية**، لا «المستخدم ضغط Start» — مطابقةً لـ`MonitoringClaim`؛ (ج) **القياس غير مؤجَّل ولا مشروط**: صحة G5 تُثبت بـ `start → FGS → registration → notification → حالة Motion فعلية → stop` على RMX3910، ثم تُقاس على حدة (زمن البدء، استمرارية العينات، CPU، أثر البطارية، سلوك الشاشة المطفأة) في حملة **مفصولة عن اختبارات correctness** — البطارية ليست شرطًا لإثبات G5 ولا ذريعة لتعليقها. **مبدأ العمل المعتمد من الآن: «التسريع المنضبط»** — نتحقق مما يمسّ صحة المعمارية أو سلامة المستخدم، ولا نضيف حواجز جديدة بلا فائدة. **نقل البوابات مؤجَّل إلى A-19** (كان مخططًا له أن يكون A-18 قبل اعتماد القرارات) **وينزل في نفس commit كود الخدمة**: §18 (منع `<service>`/`startForeground`/`FOREGROUND_SERVICE`/`foregroundServiceType`/`specialUse`)، و«إذن `<uses-permission>` واحد بالضبط» ← ثلاثة مسمّاة (`POST_NOTIFICATIONS` + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`)، وتضييق قائمة ثوابت §15 لتقبل الأرقام الستة **داخل كائن المعايرة المسمّى وحده**، و§19 يبقى harness القياس مقصورًا على الاختبار | قرار المالك الحرفي (2026-10-01): «**Q-G5-a = 11.0 ✅**… هذا يجعل القيمة مشتقة من المسار المقاس بدل رقم عشوائي… وإذا ارتفع median على الجهاز، يبقى السقف المطلق 100 ms هو الحاكم» · «**Q-G5-b = fail closed عند رفض POST_NOTIFICATIONS ✅**… Android يسمح باستمرار FGS حتى مع رفض إشعار `POST_NOTIFICATIONS`، لكن إشعار FGS لا يظهر… **لا نبدأ Monitoring ثم نترك المستخدم دون الإشعار الذي وعدناه به**… ولا علاقة لذلك بمسار Panic» · «**Q-G5-c = إعادة استخدام نفس الحدث غير النهائي ✅، لكن Motion-scoped**… لا أوافق على استعمال `first non-terminal event` بشكل عام… أما حدث Lock أو SIM نشط فلا يجوز لموجة Motion أن ترتبط به… بدون اختراع cooldown» · «**صاحب الـAdapter يجب أن يكون واحدًا**… لا أريد نسختين أو مالكين» · «**لا تجعل `MotionRuntime` هو مصدر الحقيقة**» · «**قياس battery/CPU ليس شرطًا لإثبات صحة G5 الأساسية**… نفصل هذه القياسات عن اختبارات correctness» · «**الآن تحديدًا ننتقل إلى مبدأ التسريع المنضبط… لا أريد تحويله إلى سلسلة توقفات إضافية**» · «**إذن G5 معتمد… عدّل فقط النقاط الثلاث، ثم ابدأ مرحلة RED للاختبارات، وليس GREEN مباشرة**» |

| A-19 | 2026-10-01 | §17 + §18 + §23 (الدفعة G، شريحة G5: انتقالة البوابات مع كود الخدمة) | ما كان سيحدث بلا هذا التعديل: كان كود G5 سيُسقط قيود `g4b-review-check.sh` (§5 إذن واحد، و§10/§15/§18 منع `<service>`/`FOREGROUND_SERVICE`/`specialUse`) وقيود `phase3-review-check.sh` بحذفٍ صامت داخل نفس الـcommit، فتُقرأ انتقالة البوابة كتليين لإرضاء CI — وهو ما تمنعه A-8 حرفيًا «ممنوع تعديل أذونات/بوابات وحده لإرضاء فحص ثابت»؛ أو كان السجل سيُكتب بعد الكود فيصبح تبريرًا لاحقًا لقرار حُسم أصلًا في A-18 | **انتقالة البوابات مسجّلة هنا صراحةً، وتنزل في نفس commit كود الخدمة كما حُسم في A-18.** ما يتغيّر (ولا شيء منه ترخٍ بابًا): (1) **§5 و`phase3`**: بدل «إذن `<uses-permission>` واحد بالضبط» ← **الثلاثة المسمّاة حرفيًا** `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`، وأي رابع يستلزم تعديلًا جديدًا هنا؛ (2) **§10**: `<service>` و`FOREGROUND_SERVICE` مسموحان **للكائن المعتمد وحده** `com.shieldra.detection.MotionMonitoringService` مع `android:foregroundServiceType="specialUse"` و`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`، ومنع WorkManager يبقى كما هو؛ (3) **§15**: `MotionSensorAdapter(` مسموح في ملف الخدمة وحده (مالك واحد عملاً بـA-18 (أ))، ويُسقط من قائمة المنع `<service` و`FOREGROUND_SERVICE` **مع إبقاء `RECEIVE_BOOT_COMPLETED`/`BOOT_COMPLETED`** (A-10 قائم)؛ (4) **§16**: يبقى منع الـwake lock وإعفاء البطارية كما هو، ويُضيّق منع المانيفست إلى «لا خدمة سوى الخدمة المعتمدة» لأن حملة G4-A أُغلق قياسها بتقريرها؛ (5) **§18**: مسح `<service`/`startForeground`/`FOREGROUND_SERVICE`/`foregroundServiceType`/`SPECIAL_USE`/`specialUse` يصبح **اسميًّا**: مسموح في ملف الخدمة والمانيفست فقط ومحظور في أي موضع آخر من `app/src/main`، وسماح استُدعاء الـseam يقتصر على `MotionGuardPipeline.kt` و`MotionMonitoringService.kt`، و**§19 لا يُمَسّ** (harness القياس يبقى مقصورًا على الاختبار). **Assertions جديدة بالحد الأدنى الذي يحمي قرارات A-18 فقط**: صياغة الإشعار الدائم حرفيًا كما اعتمدتها A-9، والأرقام الستة لا تُقرأ إلا داخل `ApprovedMotionCalibration.kt` وبلا رقم سابع، ولا طلب `POST_NOTIFICATIONS` من مسار المراقبة (يُطلب من شاشة الإعدادات وحده — A-6)، وحالة Motion `Active` لا تُنتَج إلا من `MonitoringClaim` (والاختبار المُسمّى `MotionMonitoringPresentationTest` مُثبَّت في البوابة). **وسجل بصيغة الاختبار الذي نُقل:** سطر RED كان يمنع أن يستهلك ربطُ التكرار معرّف `EventId`؛ المحرّك ينتج معرّفًا عند كل تأكيد والمالك أقرّ أن المحرّك يبقى مصدر الحقيقة، فالعقد المُثبت صار «لا يُخزَّن صفٌّ ثانٍ ويُبلَّغ معرّف الحدث النشط» — لا تغيير في السلوك بل في ما يُثبته الاختبار، وهو موثّق في KDoc `MotionIncidentBinder`. **ولا يثبُت بصحة G5 بهذا السطر شيء**: الدليل المطلوب `CI → RMX3910 → Start → notification → samples → Active → Stop → Not monitoring`، وقياس زمن البدء/CPU/البطارية/الشاشة المطفأة حملة منفصلة (A-18 (ج)) | قرار المالك الحرفي (2026-10-01): «ثم: `A-19 + production service + manifest + two FGS permissions + gate transition` في **نفس commit**، كما حُسم في A-18» · «إذا كان الفشل محصورًا في الرموز غير الموجودة التي يفترض أن تنشئها GREEN، ننتقل مباشرة إلى GREEN دون دورة تحليل جديدة» · «والـGREEN الذي أعتمده هو التصميم الذي عرضه الـAI، مع ثلاث نقاط لا ينبغي أن تتغير: **Service owns adapter** / **Monitoring truth = actual service state + `adapter.isReporting`** / **Motion event reuse = active non-terminal MOTION only**» · «ولا حاجة الآن إلى إعادة قراءة التصميم أو إضافة احتياطات جديدة. **دع RED يُحسم أولًا.**» |

**ملاحظات تقييد:**

- **A-19 ينقل البوابات ولا يرخي بابًا: كل ترخٍ مقصور على الكائن المعتمد باسمه.** الخدمة `com.shieldra.detection.MotionMonitoringService` وحدها تحمل `<service>`/`startForeground`/`specialUse`، والمانيفست وحده يحمل الأذنة الثلاثة المسمّاة، و`ApprovedMotionCalibration.kt` وحده يحمل الأرقام الستة، و`MotionGuardPipeline.kt` و`MotionMonitoringService.kt` وحدهما يستدعيان seam الحركة. ما عدا ذلك يبقى ممنوعًا كما كان: لا `RECEIVE_BOOT_COMPLETED`/`BOOT_COMPLETED` (A-10)، لا wake lock ولا إعفاء بطارية (A-13)، لا WorkManager (F3/§17)، لا إرضاءً لفحص ثابت (A-8). أي توسيع لاحق — إذن رابع، خدمة ثانية، رقم سابع — يعدّل هذا السجل أولًا.
- **A-19 لا يدّعي أن G5 صحت.** السطر يصرّح بكتابة الكود وبإنزال البوابة معه في نفس الـcommit؛ صحة الشريحة تُثبت بالمشاهدة على RMX3910 (`Start → notification → samples → Active → Stop → Not monitoring`) وبعدها تُقاس التكلفة (زمن البدء، استمرارية العينات، CPU، البطارية، سلوك الشاشة المطفأة) في حملة منفصلة كما أقرّ A-18 (ج). قبل تلك المشاهدة لا يجوز أي تقرير يقول إن المراقبة «تعمل» أو إن الحركة «تُكتشَف».
- **A-19 لا يغيّر صدق الواجهة (§31).** `Motion Guard — Active` لا تُنتَج إلا من `MonitoringClaim` الذي ينشره الكائن الحيّ (حالة الخدمة + `adapter.isReporting`)؛ وضغط Start نفسه لا ينتج حالة، ورسالة «توقفت المراقبة» تُعرض فقط بعد أن تُرفع الخدمة نفسها من القائمة (A-18 (ب)). صياغة الإشعار الدائم تبقى A-9 حرفيًا وبلا «24/7» ولا «مضمونة».
- **التخفيف المُعلَن في عقد الاختبار ليس تخفيفًا في السلوك.** كان سطر RED يقول إن ربط تكرار الحركة لا يستهلك معرّف `EventId`؛ والمحرّك المعتمد (المالك: «مصدر الحقيقة هو المحرّك») يُنتج معرّفًا عند كل تأكيد قبل أن يُقرر الرابط أيّ صفّ يكتب. الاختبار المُثبَّت الآن يمنع ما يهم فعلًا: **لا يُخزَّن صفٌّ ثانٍ، والمُبلَّغ هو معرّف الحدث النشط**. لم تُغيَّر البوابة لتقبل كودًا، بل عُكس في السجل ما يقبله الاختبار.
- **أثر سلبي مُعلَن لقرار A-18 (ج) بحرفيته:** حدث `MOTION_CONFIRMED_EVENT` غير النهائي لا يبلغ حالة نهائية في قواعد اليوم (لا قناة تسليم بعد — §30)، لذلك يبقى **حدث Motion واحد مفتوح يمتص كل تأكيد لاحق** حتى يُغلق شيءٌ ما حالته. هذا هو حرف «أعد استعمال الحدث غير النهائي من نوع MOTION، وبلا cooldown جديد» لا انحراف عنه؛ ولا يُصلَح بإضافة cooldown ولا برقم سابع، بل بقرار تسليم أو قرار إبطال من المالك (القائمة في `docs/OWNER_QUESTIONS.md`).
- **A-18 يُغلق Q-G5-a/b/c ويعتمد تصميم G5 بثلاثة تصحيحات، ولا يُرخي أي بوابة.** الترتيب بعد A-18: **RED** (اختبارات JVM تفشل على رموز غير موجودة) ← ثم **GREEN** الذي ينزل فيه **A-19** ناقلَ البوابة **في نفس commit** كودَ الخدمة. حتى ينزل A-19 تبقى `g4b-review-check.sh` §18 (منع `<service>`/`startForeground`/`FOREGROUND_SERVICE`/`foregroundServiceType`/`specialUse`) و«إذن `<uses-permission>` واحد بالضبط» وقائمة ثوابت §15 و§19 **سارية كما هي حرفيًا**. ومبدأ المالك «التسريع المنضبط» يُلزم A-19 بأقل عدد من Assertions جديدة يحمي قرارات A-18 هذه فقط — لا حواجز بلا فائدة.
- **الأرقام الستة هي كل معايرة الحركة الإنتاجية** (`4.6 / 0.8 / 500 / 100 / 20_000 / 11.0`). لا سابع ولا تبديل لواحد إلا بتعديل جديد هنا (§14، §37)، و`11.0` **اشتقاق من مسار مقاس** لا تفضيل: عند وسيط ≈9 ms يعطي ≈99 ms فيُطابق سقف `maxSampleGapMs = 100`؛ ومتى ارتفع الوسيط بقي السقف المطلق 100 ms هو الحاكم، فلا يُرفَع العامل ليغطي حالة أرادت المعايرة رفضها.
- **صياغة `Q-G5-b` ممنوعة من أن تتحوّل إلى ادعاء.** السبب منتَجِي: Android **يسمح** باستمرار الـFGS رغم رفض `POST_NOTIFICATIONS`، لكن إشعاره لا يظهر للمستخدم، وA-15(3) يشرط أن يُدرك المستخدم أن المراقبة تعمل. لذلك **لا نبدأ مراقبة بلا إشعارها الموعود**، ونعرض سببًا واضحًا وتوجيهًا إلى الإعدادات. هذا **لا يمسّ A-6**: رفض الإذن لا يمنع تسجيل Security Event ولا يغيّر مسار Panic.
- **`Q-G5-c` ليس cooldown ولا سياسة تكرار عامة.** الربط **بنوع الحركة وحده**: موجة Motion مرتبطة بحركة غير نهائية تعيد استعمال `EventId` ذاك، ولا يجوز أن تجد أي حدث غير نهائي (Lock/SIM/Battery/Panic) لترتبط به — وهو الخطر الذي نوقش في `PanicIncidentCoordinator`؛ وبعد حدث نهائي يُسكَب `EventId` جديد. قرار A-7 للذعر يبقى كما هو، وحقول `ApprovedGuardRules` لا تُمَسّ.
- **مالك واحد للحساس، ومصدر واحد للحقيقة.** `MotionMonitoringService` يملك `MotionSensorAdapter`؛ و`MotionRuntime` يملك حالة/تحكّم المراقبة فقط ولا يملك نسخة ثانية من المحوّل (لا تضارب `isReporting` ولا unregister مزدوج). والادعاء `ActiveGuardMonitoring` يُشتق من **حالة الخدمة الفعلية + `adapter.isReporting` الفعلية**، لا من «المستخدم ضغط Start» — مطابقةً لقاعدة `MonitoringClaim` وقاعدة §31.
- **قياس البطارية/الـCPU ليس شرطًا لصحة G5 ولا ذريعة لتأجيلها** (تصحيح المالك الثالث): تُثبت صحة G5 بـ `start → FGS → registration → notification → Motion state فعلي → stop` على RMX3910، ثم تُقاس على حدة: زمن البدء، استمرارية العينات، CPU، أثر البطارية، سلوك الشاشة المطفأة — في حملة مفصولة عن اختبارات correctness.
- **A-18 لا يغيّر صدق الواجهة.** حتى ينزل GREEN ويُثبت الجهاز، تبقى اللوحة **Motion Guard — Not monitoring yet**؛ وصف Motion بـ Active لا يُنتَج إلا من الحالة الحقيقية (الملاحظة المباشرة لخدمة حقيقية تعمل وتسجيلها قائم).

- **A-17 يُغلق Q-G1 بالكامل، والخمس أرقام هي كل المعايرة الإنتاجية.** النص الأصلي أعلاه (سطر A-16) الذي كان يقول إن `noiseFloorDeltaMs2` و`minEpisodeMs` و`maxSampleGapMs` و`requestedReportPeriodUs` «ما زالت قرار المالك» صار مُنفَّذًا بـA-17: القيم المعتمدة `4.6 / 0.8 / 500 / 100 / 20_000` تُكتب كما هي، و**لا رقم سادس ولا تغيير رقم** إلا بتعديل جديد هنا بتاريخه وسببه (§14، §37). ولا يُشتق أي منها من قياس وحده.
- **فصل ملزِم: `minEpisodeMs` ليست سياسة تأكيد.** `minEpisodeMs = 500` تُعرِّف متى تُحسب حلقة حركة أصلًا (تجميع episode داخل `MotionWindowEvaluator`)، وقاعدة **G1-02** في `ApprovedGuardRules` (≈3 s متصلة أو حالتان suspicious في 10 s) هي **سياسة التأكيد** التي تُنتج Security Event. **ممنوع**: أن يخفض أي كود الثانية لأن الأولى موجودة، أو أن يعتبر حلقة 500 ms حدثًا مؤكدًا، أو أن يستعمل الأولى بديلًا عن الثانية. حقول `ApprovedGuardRules` الثلاثة الخاصة بالحركة لا تُمَسّ بـA-17.
- **أساس `100 ms` مُسجَّل كي لا «يُصحَّح» صعودًا لاحقًا.** أطول فجوة مقيسة على RMX3910 كانت 10 ms عند وسيط 9.0 ms، وأبطأ نافذة مرجعية مقيسة وسيطها 19.0 ms؛ فـ100 ms تعطي هامشًا ≈10× لأسوأ فجوة مقيسة دون ربط حركتين منفصلتين داخل حدث واحد. **رفض المالك لـ250 ms** جزء من السجل، واستعمال فجوة أكبر يحتاج قياسًا جديدًا وتسجيلًا، لا استنتاجًا من «الحارس لا يعمل».
- **نوع الـFGS مُعتمد: `specialUse`، والتزاماته الأربعة تدخل معه.** `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` + `android:foregroundServiceType="specialUse"` + `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` ببيان غرض حقيقي، مع تصريح مراجعة Google Play. **تصحيح المالك مقبول ومُطبَّق في التقرير**: لـ`health` مسار تقني عبر `HIGH_SAMPLING_RATE_SENSORS` أيضًا، وسبب استبعاده **غرضه الموثّق (لياقة/صحة)** لا شكل الإذن؛ ولا يُختار نوع FGS بمقارنة أذونات بينما يُخطئ في وصف حالة الاستخدام (روح A-8).
- **مدة الإبلاغ `20_000` µs طلبٌ لا عقد.** تُمرَّر كما اعتمدها المالك، ويبقى المعدل الفعلي محسوبًا من الطوابع الموصَلة (A-12): لا 106 Hz ثابتًا ولا أي رقم موصَّل يُعامل كالمطلوب.
- **A-17 لا يصرّح بأي كود خدمة.** انتقالة البوابات (كانت مُسمّاة A-18 في هذا السطر، **وأُعيد ترقيمها إلى A-19** حين صار A-18 سجلَ قرارات G5) تنزل **في نفس commit** الخدمةَ الحقيقية، لا قبلها: تُبدّل `g4b-review-check.sh` «إذن `<uses-permission>` واحد بالضبط» بالأذنة الثلاثة المسمّاة (`POST_NOTIFICATIONS` + `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`)، وتُضيّق قائمة ثوابت §15 المحظورة لتسمح بأرقام A-17 **وأرقام A-18** داخل كائن المعايرة المسمّى وحده وبلا أي `requestedReportPeriodUs: Int = ` default، وتُبقي منع `<service>`/`startForeground`/`FOREGROUND_SERVICE`/`foregroundServiceType`/`specialUse` ساريًا حتى وجود الكائن نفسه. **ممنوع**: خدمة فارغة، أو تعديل أذونات/بوابات وحده لإرضاء فحص ثابت (A-8 حرفيًا). والترتيب بعد A-17: تصميم شريحة G5 وعرضه على المالك ← موافقته (تمّت بـA-18) ← ثم الكود.
- **A-17 لا يغيّر صدق الواجهة (§31).** اللوحة تبقى **Motion Guard — Not monitoring yet** ولا تظهر Motion كـ Active/Protected حتى وجود خدمة حقيقية مُثبتة على جهاز المالك، و`MonitoringClaim` كما هو. كما لا يمسّ A-9 (صياغة الإشعار) ولا A-10 (لا إقلاع تلقائي) ولا A-11 (توصية البطارية اختيارية) ولا §21–§23 (لا كاميرا ولا موقع ولا قناة خارجية).

- **A-16 (النص الأصلي؛ أُغلق ما فيه بالباقية بـA-17) يُغلق نصف Q-G1 ولا يُغلق السؤال كله.** الأرقام الباقية التي لا تتكوّن منها `MotionCalibration` شحنًا كانت قرار المالك: `noiseFloorDeltaMs2` (ما يُعدّ ضجيجًا — وهو النصف الثاني من سؤال Q-G1 حرفيًا، ويعمل في الكود كـ hysteresis يُبقي الحلقة مفتوحة بعد تجاوز 4.6)، و`minEpisodeMs`، و`maxSampleGapMs`، و`requestedReportPeriodUs` (مدة إبلاغ الحساس) — **جاء A-17 فاعتمدها، وأضاف A-18 رقمًا سادسًا (`stallToleranceFactor = 11.0`) فأصبحت الست كاملة**. يبقى ساريًا من هذا السطر: **لا يُكتب أي حرف `MotionCalibration(...)` بقيمة إنتاجية في `app/src/main`** إلا من كائن المعايرة المعتمد باسمه في A-17/A-18، ولا يخترعها agent ولا يرثها من قياس وحده (§37).
- **A-16 لا يُرخي أي بوابة ولا يصرّح بأي كود بعد.** منع `g4b-review-check.sh` §18 (`<service>`/`startForeground`/`FOREGROUND_SERVICE`/`foregroundServiceType`/`specialUse` = فشل) **يبقى ساريًا كما هو**؛ انتقالة البوابة عند بدء كود الخدمة يجب أن تظهر كتعديل مستقل في هذا السجل (§14)، لا كسطر يُحذف من سكربت. ترتيب العمل بعد A-16: مراجعة النوع (موثَّقة الآن) ← إجابة المالك عن الأرقام الباقية (تمّت بـA-17) ← تصميم شريحة G5 وعرضه ← ثم الكود.
- **أثر 4.6 مقيس ومُسجَّل كي لا «يُصحَّح» لاحقًا.** عند 4.6 لم تتجاوز عينات الحمل الطبيعي إلا 3 من 2112 (أطول سلسلة متصلة فوق 3.0 = 19 ms)، أي أن Motion لن يتسلّح على حملٍ أو مشيٍ عادي وسيتسلّح عند مستوى خطف/ضرب الهاتف. هذا مقصود المالك؛ تخفيض الرقم لاحقًا يحتاج قياسًا جديدًا وتسجيلًا هنا، لا استنتاجًا من «الحارس لا يعمل».
- **A-16 لا يغيّر صدق الواجهة.** حتى اكتمال المعايرة ووجود خدمة حقيقية مُثبتة على جهاز المالك، تبقى اللوحة **Motion Guard — Not monitoring yet** ولا تظهر Motion كـ Active/Protected (§31، و`MonitoringClaim` كما هو).
- **A-16 لا يمسّ A-9 ولا A-10 ولا A-11 ولا §21–§23.** صياغة الإشعار الدائم كما هي، ولا إقلاع تلقائي، وتوصية البطارية اختيارية، ولا كاميرا ولا موقع ولا قناة خارجية.

- **A-15 قرارٌ في المعمارية، لا إذنٌ بكتابة الكود.** الترتيب الملزِم بعد هذا التعديل: **A-15 (سجل القرار) ← مُقيِّم G4 النقي (بلا عتبة افتراضية) ← قياس Q-G1 (حملٌ مقصود واحد على الجهاز) ← اعتماد المالك لعتبة الحركة القوية ← ثم تنفيذ خدمة الـFGS الحقيقية التي تربط الكاشف بالإشعار الدائم بحالة مراقبة فعلية**. لا يُضاف `<service>` ولا `FOREGROUND_SERVICE` ولا `FOREGROUND_SERVICE_SPECIAL_USE` ولا أي تغيير على إشعارات المراقبة ولا أي تنشيط مراقبة وقت التشغيل قبل الخطوة الأخيرة. البوابات الثابتة (`g4b-review-check.sh`: إذن `<uses-permission>` واحد بالضبط، و`<service>` = فشل، و`FOREGROUND_SERVICE` = فشل؛ وكذلك `group-c-contract-check.sh`) **تبقى كما هي ولا تُرخى لتسهيل كود مستقبلي**؛ أي ترخٍ للبوابات لاحقًا يجب أن يكون تعديلًا صريحًا في هذا السجل، لا سطرًا يُحذف من سكربت.
- **A-15 لا يجيب Q-G1.** عتبة «الحركة القوية» ما زالت غير معتمدة، ولا يُشتق أي رقم من قياس تلقائيًا؛ والمُقيِّم النقي يبقى بحقول محقونة وبلا أي default إنتاجي، عملاً بـA-12 وA-13.
- **A-15 لا يمسّ A-9 ولا A-10 ولا A-11.** يبقى الإشعار الدائم دقيقًا بلا وعد 24/7، ويبقى الإقلاع التلقائي مرفوضًا في هذه المرحلة، وتبقى توصية تحسين البطارية اختيارية لا شرطًا ولا ضمانًا.
- **A-15 لا يغيّر صدق الواجهة.** ما دامت عتبة Q-G1 غير معتمدة ولا كاشف موصول بخدمة حقيقية، يجب أن تقول اللوحة **Motion Guard — Not monitoring yet**؛ ولا تُستبدل بـ Active/Protected/Monitoring/Guard enabled لمجرد وجود مُقيِّم نقي، وتظل فصل `MonitoringClaim` ساريًا.

- **A-14 لا يُرخي A-8 ولا A-13 ولا §21–§23.** يضيف قواعد تصنيف الأدلة وترتيب العمل فقط؛ لا يتخذ قرار FGS، ولا يمسّ منع الكاميرا/الموقع، ولا يغيّر قاعدة A-13 التي تقول إن نتيجة استمرار التوصيل أو انقطاعه لا تثبت أيًّا من أمرَي المعمارية.

- **A-13 لا يغيّر أي قرار قائم.** لا يمسّ A-8 (متى يُضاف FGS، وهو لم يُضَف)، ولا سياسة الإقلاع A-10، ولا سياسة تحسين البطارية A-11، ولا سياسة حدث الحركة الأمني أو الخطورة، ولا ادعاء المراقبة المستمرة، ولا اختيار نوع FGS. A-13 يوفّر دليلاً لقرار لاحق ولا يتخذه.
- **A-13 هو القيد الحرفي على مركَبة القياس.** لا `app/src/main` في هذه الخطوة: المركَبة ملفات `app/src/androidTest` وبوابة ثابتة فقط؛ ويُمنع في الحملة كلها `<service>` أو `FOREGROUND_SERVICE*` أو `RECEIVE_BOOT_COMPLETED` أو `WAKE_LOCK`/`newWakeLock` أو إعفاءٌ من تحسين البطارية أو إشعارٌ أو إذنٌ جديدٌ أو سطحٌ إنتاجي. ولا تُرخى G4-B لتسهيل القياس.
- **قاعدة التصنيف الملزِمة لـA-13، تُستخدم كما كُتبت قبل أي استعمال:** لا يُسمَّى تشغيل «متوقفًا» لأن فترة واحدة كانت كبيرة. التصنيف الاختباري الوحيد المعتمد لكل نافذة: `NO_DATA` إن لم تصل أي عيّنة؛ `CONTINUING` إن وصلت عينتان فأكثر وكانت أطول فترة ≤ 3× وسيط الفترات وكانت آخر عيّنة ضمن نفس التسامح من نهاية النافذة؛ `INTERRUPTED` خلاف ذلك. العامل 3.0 معاملٌ اختباري محقون للنوافذ لا عتبة كشف، ولا يعني «هجومًا»، ولا يرفعه أي سطح إنتاجي، ولا يُشتق منه «معدل مقبول» عام.
- **حدود A-13 تُذكر في كل تقرير صادر عنه:** لأن instrumentation تُبقي العملية حيّة بأولوية اختبارية، تصف النتيجة «توصيلًا إلى عملية اختبار بشاشة مطفأة» ولا تصف نجاة خدمة مراقبة حقيقية؛ وأي استنتاج معماري يحتاج تحليل Android/OEM لاحقًا. التشغيل الذي يتلوّث بحركة أو لمس أو إعادة تشغيل أو انقطاع عملية يُعلَّم ملوّثًا ويُعاد، ولا يُعرض كبديل صالح.
- **A-13 يثبّت الترتيب:** G4-A (قياس الاستمرارية) → تحليل → قرار FGS، ثم حملة نصف Q-G1 القوي (حملٌ مقصود واحد) لاحقًا؛ ولا تُدمج الحملة القياسية مع حملة العتبة.
- **A-12 لا يُرخي A-8 ولا §23.** لا يُضاف في G3 أي `<service>` ولا إذن ولا خاصية FGS؛ المحوّل يُستدعى من اختبارات instrumented فقط ولا يصل بأي سطح مراقبة دائم، والبوابات الثابتة تبقى كما هي.
- **A-12 يفصل المصدر عن الدلالة.** مخرجات G3 لا ترفع خطورة ولا تُنشئ حدثًا ولا تكتب قاعدة؛ وأي شاشة أو حدث لاحق يجب أن يأتي من طبقة تحقق معتمدة، لا من بيانات الإشارة مباشرة.
- **A-12 يُبقي Q-G1 مفتوحًا.** قياس أرضية ضجيج السكون على RMX3910 ليس عتبة شحن؛ مستوى الحركة القوية لم يُقَس بعد (يحتاج حملًا مقصودًا واحدًا) ويبقى قرار المالك.

- A-2 لا يُلغي §2.5: يبقى **ممنوعًا** أي ارتداد إلى مفتاح برمجي. حذف المفتاح
  يجعل الأدلة السابقة **غير قابلة للقراءة نهائيًا**، ولا يفتح أي مسار قراءة.
- A-3 لا يُلغي §4.3: قاعدة "لا تدمّر قاعدة البيانات" تظل سارية لكل مسارات
  الفشل الأخرى. إعادة الضبط ليست مسار استرجاع ولا تُستخدم بعد فشل.
- خطوة `delete Keystore evidence key` في §5.7 تُقرأ مع A-2 كأنها إجرائية **مطلوبة**، لا
  اختيارية، ولم تُنفَّذ قبل هذا السجل.
- A-4 لا يُلغي A-2 ولا يُرخي §6: إعادة الضبط المؤكَّدة تظل تحذف الأدلة نفسها، فتبقى
  قراءتها UNAVAILABLE نهائيًا. RECOVERY_REQUIRED تخصّ فقط الفقد غير المتعمَّد للمفتاح مع
  بقاء الـciphertext؛ ولا يفتح أي مسار ارتداد إلى مفتاح برمجي (§2.5).
- ربط §15 (شريحة `4d-storage-wiring-gate`): تُوصَّل التخزينات الآن إلى التطبيق خلف
  بوابة `StorageRuntimeApproval.approved` بقيفتها الافتراضية `false`. هذا **ليس** اعتماد
  الإنتاج (§15) ولا توقيع G4؛ يبقى §15/G4 مفتوحين حتى يقلب المالك العلامة في commit
  ظاهر (§14) **و** تُستكمل بقية البوابات (محوّل Lock Guard والمرحلة 5/6/7 ومجموعة التدقيقات).
  البناء الافتراضي لا يفتح التخزين إطلاقًا (مثبت على الجهاز).
  **تحديث A-5 (2026-09-30):** القلب إلى `true` تم في commit ظاهر، فالبناء المعتمد الآن
  يفتح التخزين عند بدء العملية؛ لكن §15/G4 يبقيان مفتوحين حتى استكمال بقية البوابات.
- إسقاط العرض المُلفَّق (`4d+-lock-guard-adapter`): كان التطبيق يعرض `DemoData` وقت
  التشغيل، أي درع "Protected" وأربعة حراس "Active" وشحن 84% وشبكة Wi-Fi وحدثَين
  مخترَعين، مع غياب أي كشف حقيقي. صار العرض مشتقًّا من `LockGuardDecision` فقط،
  **القاعدة:** ما دام لا يوجد كشف، لا يعرض التطبيق "Protected" ولا حارسًا "Active" أبدًا،
  وإلا يعرض حالة معطّلة صادقة. هذا يقلّل ما يدّعيه التطبيق ولا يغيّر سياسة (§14).
  قراءة الأحداث المحفوظة الحقيقية ومحوّل الكشف والخدمات والتنبيهات **مؤجَّلة** إلى دفعات
  لاحقة؛ §15/G4 يبقيان مفتوحين.
- مسار قراءة حقيقي (`4d-persisted-event-reader`): صارت الأحداث المحفوظة تُقرأ فعلًا
  وتصل الواجهة. القواعد المفروضة: **لا يُختلق دليل** (لا صورة ولا موقع ولا بطارية ولا
  إيصال توصيل إذا لم تكن مخزَّنة)، و**لا استعلام Room على الخيط الرئيسي** (يرمي Room
  استثناءً هناك، والقراءة تُرسل إلى `Dispatchers.IO`)، و**البناء الذي لم يُثبَّت فيه
  التخزين لا يقرأ شيئًا** إطلاقًا. **لم يُقلَب `StorageRuntimeApproval.approved`** ولا
  تُغيَّرت سياسة؛ §15/G4 يبقيان مفتوحين. إنتاج الأحداث وقت التشغيل (الكشف) مازال
  موقوفًا على إجابات G2/G3.
- **A-6 لا يُلغي §23 ولا §31، بل يجعلهما هما القيدَين الملزِمين.** الإذن يُطلب من
  ميزة الإشعارات عند إعدادها، لا من شاشة الترحيب ولا عند ضغطة Panic؛ ورفضه لا يُسقط
  تسجيل الحدث. حالة الإذن تُفحص قبل كل محاولة نشر، ونتائج Android لا تُقرأ كتأكيد
  مشاهدة أو وصول.
- **A-6 لا يفتح بابًا لأي قناة خارجية.** لا FCM ولا سحابة ولا SMS ولا بريد في هذه
  الدفعة (مؤجَّل صراحةً). ما يُنفَّذ محليًا فقط على جهاز المستخدم.
- **A-7 لا يغيّر مرجع الهوية.** `EventId` ودورة حياة الحدث يظلان المرجعَين الوحيدَين؛
  عدّاد التفعيلات صار **معلومة مخزَّنة** على الحدث نفسه، لا محفّزًا لإنشاء حدث آخر ولا
  سببًا لرفع الخطورة ولا نافذة زمنية.
- **وعد "zero permissions" في وصف المتجر كان وعدًا نسبيًا لا مطلقًا** (الأذونات تُطلب
  عند الحاجة فقط). بعد A-6 تُصحَّح المواضع المرتبطة بهذا الوعد فقط إلى صياغة صادقة،
  ولا تُعاد كتابة وصف المتجر كاملًا.
- **A-8 لا يُرخي بوابات الدفعة F.** يبقى القيد: لا يُكتب `<service>` ولا `FOREGROUND_SERVICE*`
  في أي commit حتى يوجد كاشف حركة حقيقي يعمل ويُثبت ذلك على جهاز المالك. البوابات الثابتة
  (`g4b-review-check.sh`) تبقى كما هي، وأي توسيع لاحق لها يجب أن يظهر كتعديل مسجَّل هنا لا
  كتعديل صامت في السكربت.
- **A-8 يشترط الدقّة قبل الاختيار.** إذا تبيّن من متطلبات الكاشف الحقيقي أن نوعًا أكثر
  تحديدًا هو الصائب (مثلاً نوع يغطي القراءة المستمرة للحساسات)، يُستخدم ذلك النوع ويُصحَّح
  السجل؛ `specialUse` ليس خيارًا افتراضيًا دائمًا بل خيار "لا يوجد أنسب".
- **A-9 لا يُلغي §31.** الإشعار المستمر يصف حالة موجودة فقط: إن لم تكن المراقبة شغّالة فلا
  إشعار "active"، ولا يُدَّعى وصول ولا ضمان دائم.
- **A-10 يُبقي §23 ساريًا.** لا إذن إقلاع ولا أي إذن جديد قبل الميزة التي تحتاجه فعلًا.
- **A-11 قيد لفظي لا تقني.** نصّ التوصية الاختيارية هو المعتمد حرفيًا؛ ممنوع أي صياغة تجعل
  تعطيل تحسين البطارية شرطًا للعمل، وممنوع الادعاء الحتمي أن النظام سيقتل الخدمة بدونه.

**Received:** 2026-09-28
**Source:** Owner decisions taken during the 4d-evidence-lifecycle batch
**Authority level:** BINDING — these amendments supersede the original text of
§5.4 and §5.7 on the points listed above, and nothing else.

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
