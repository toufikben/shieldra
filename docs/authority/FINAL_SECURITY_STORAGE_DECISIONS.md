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

**ملاحظات تقييد:**

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
