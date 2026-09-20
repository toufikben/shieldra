هذا الأمر مخصص لـ AI GitHub Agent.

SHIELDRA — PHASE 1 / GROUP C

Core Contracts & Boundaries — EXECUTE NOW

يجب عليك القيام بتنفيذ GROUP C الآن فعليًا داخل:

"https://github.com/toufikben/shieldra"

آخر Group B مكتمل، وآخر commit معروف:

"871e190de63337b0d1f695145fe38518435f37b0"

لا تعِد تنفيذ Group A أو Group B.

---

1. AUTHORITY

يجب عليك الالتزام بالترتيب:

1. Product Freeze v5
2. Phase 1 Specification
3. Group C specification
4. Android platform requirements
5. Google Play requirements
6. Verified technical evidence
7. Relevant skills

المشروع جديد بالكامل.

ممنوع استخدام أو تحليل أو نسخ أي شيء من:

"phone_fortess_v2_pro"

لا Legacy Architecture، لا Legacy Code، لا Legacy Database، لا Legacy UI.

---

2. OBJECTIVE

يجب عليك تثبيت العقود الأساسية والحدود المعمارية الحقيقية التي ستبني عليها المراحل التالية.

Group C ليس Feature implementation.

يجب ألا تنفذ أي سلوك إنتاجي متعلق بـ:

- Failed Unlock
- Motion Detection
- SIM Detection
- Battery Emergency
- Camera
- Location collection
- Evidence capture
- SMTP
- WhatsApp
- Telegram
- Smart Queue
- Safe Zones
- Geofencing
- Continuous GPS
- Production notifications
- Production billing
- Ads SDK
- Final UI

يمكن إنشاء interfaces، data models، enums، value objects، repositories، boundaries وtestable contracts فقط عندما تكون مطلوبة.

---

3. FIRST — INSPECT

قبل التعديل يجب عليك:

- قراءة Group B architecture.
- فحص project tree.
- فحص existing contracts.
- فحص dependencies.
- فحص Git state.
- فحص Group B report.
- فحص skills المتاحة ذات الصلة.

لا تعيد إنشاء شيء موجود بشكل صحيح.

إذا وجدت تعارضًا مع Product Freeze، يجب إصلاحه في نطاق Group C وتوثيقه.

---

4. CORE DOMAIN CONTRACTS

يجب عليك تعريف العقود الأساسية بوضوح.

Signal

يجب أن يمثل:

- type
- strength
- timestamp
- context

Strength يجب أن يكون محصورًا في:

"STRONG"
"MEDIUM"
"WEAK"

ويجب أن يكون Signal بيانات فقط ولا ينشئ Security Event بنفسه.

---

5. SECURITY EVENT CONTRACT

يجب إنشاء نموذج Security Event قابل للتوسع.

يجب أن يتضمن على الأقل:

- unique event UUID
- event type
- creation timestamp
- current state
- evidence references
- delivery information عند الحاجة
- metadata الضرورية فقط

لا تضف بيانات شخصية أو حساسة غير مطلوبة.

يجب أن يكون UUID لكل Event مستقلًا.

---

6. EVENT STATE CONTRACT

ثبت الحالات:

"INITIATED"
"COLLECTING"
"READY"
"DELIVERING"
"DELIVERED"
"DEFERRED"
"FAILED_FINAL"

يجب أن تكون transitions قابلة للتوثيق والاختبار.

القواعد:

- DEFERRED ليس terminal.
- DEFERRED يمكن أن يعود للمعالجة لاحقًا.
- FAILED_FINAL terminal.
- لا تسمح بعلاقات state غير منطقية دون توثيق.
- لا تنفذ Smart Queue behavior الآن.

---

7. SECURITY SIGNAL TYPES

يجب إنشاء النوع الأساسي للـSignals بحيث يسمح لاحقًا بـ:

- Lock Guard
- Motion Guard
- SIM Guard
- Battery Guard
- Panic Guard

لكن:

لا تنفذ Detection Logic الآن.

Guards لاحقًا تنتج Signals فقط.

---

8. PROTECTION STATE BOUNDARY

يجب تعريف boundary واضح لـ:

"ProtectionStateEngine"

ويجب أن يكون معماريًا:

الجهة الوحيدة التي يمكنها إنشاء "CONFIRMED_SECURITY_EVENT".

لا تسمح لأي Guard أو UI أو Service بإنشاء Confirmed Security Event مباشرة.

لا تنفذ rules الفعلية الخاصة بالـSecurity Engine في Group C؛ فقط contract/boundary.

---

9. EVENT PIPELINE BOUNDARY

يجب تعريف:

"EventPipeline"

وظيفته المستقبلية فصل:

Signal/Event creation
عن
Evidence collection
عن
Delivery.

لا تجعل EventPipeline مسؤولًا عن:

- UI
- Camera implementation
- SMTP
- WhatsApp
- Telegram
- network delivery

في Group C.

---

10. EVIDENCE CONTRACTS

ثبت boundaries لـ:

"CaptureManager"

"EvidenceValidator"

"EncryptedVault"

يجب أن تكون المسؤوليات منفصلة.

Evidence

يجب أن يكون قادرًا على تمثيل:

- Photo
- Location
- Photo + Location

والقاعدة:

Metadata alone must never become persisted evidence/event.

لا تنفذ Camera أو Location collection.

---

11. LOCATION EVIDENCE CONTRACT

يجب أن يميز العقد بوضوح:

"CURRENT"

و

"LAST_KNOWN"

ويجب أن يحتوي عند الحاجة على:

- timestamp
- age
- accuracy
- source

يجب أن يكون من المستحيل أو صعبًا جدًا تسمية stale location على أنها Current بسبب تصميم النموذج.

لا تضف continuous GPS.

---

12. DELIVERY CONTRACTS

ثبت:

"DeliveryOrchestrator"

و:

"ChannelAdapter"

والمفاهيم الأساسية اللازمة لاحقًا لتمثيل:

- SUCCESS
- FAILED
- DEFERRED
- SKIPPED

يجب ألا تنفذ إرسالًا فعليًا.

لا SMTP.

لا WhatsApp.

لا Telegram.

لا network calls.

---

13. DELIVERY RECEIPT BOUNDARY

يجب تعريف contract يسمح لاحقًا بتمثيل:

- channel
- status
- timestamps
- failure information عند الحاجة

لكن:

لا يجوز إنشاء Delivery Receipt مزيف أو الادعاء بأن شيئًا تم إرساله.

---

14. AUTHENTICATION GATE

ثبت:

"AuthenticationGate"

يجب أن يكون boundary مستقلًا عن UI.

يجب أن يدعم مستقبلًا حماية العمليات الحساسة مثل:

- disable protection
- delivery configuration
- delete sensitive evidence
- other sensitive settings

لا تنفذ production authentication الآن.

---

15. REPOSITORY BOUNDARIES

إذا كانت Repository interfaces مطلوبة معماريًا، يجب تعريفها فقط.

مثلًا:

- EventRepository
- EvidenceRepository
- DeliveryRepository
- SettingsRepository

لكن لا تنشئ fake repositories أو fake persistence pretending to be production.

لا تضف mock data إلى production code.

---

16. ERROR CONTRACTS

يجب إنشاء error model واضح بدل رمي exceptions عشوائية عبر الطبقات.

يجب التمييز قدر الإمكان بين:

- invalid state
- unavailable resource
- permission restriction
- authentication required
- delivery failure
- evidence validation failure
- storage failure
- platform limitation

لكن لا تخترع حالات غير مطلوبة فقط لزيادة حجم النظام.

---

17. TIME / IDENTITY

العقود التي تعتمد على الوقت أو UUID يجب ألا تعتمد مباشرة على system APIs داخل Domain إذا كان يمكن تجنب ذلك.

استخدم abstraction مناسب عند الحاجة حتى تصبح الاختبارات deterministic.

لا تضف framework غير ضروري لهذا الغرض.

---

18. UI BOUNDARY

UI يجب ألا يستطيع:

- إنشاء Security Event مباشرة
- تعديل event state مباشرة
- تنفيذ delivery مباشرة
- الوصول إلى persistence implementation مباشرة
- تجاوز AuthenticationGate

UI يتعامل مع Domain contracts فقط.

لا تبنِ Final UI.

---

19. ANDROID BOUNDARY

Android-specific implementations يجب ألا تتسرب إلى Domain contracts إلا عند الضرورة.

مثل:

- Context
- Activity
- Service
- BroadcastReceiver
- Android location classes
- Camera APIs

يجب ألا تصبح جزءًا من Core Domain models بدون سبب معماري قوي.

---

20. SAFE ZONES — STRICT EXCLUSION

يجب البحث في المشروع عن أي remnants لـ:

- SafeZone
- ZoneGuard
- SafeZoneEvaluator
- Geofence
- GeofencingClient
- continuous GPS

إذا وجدت remnants من Group B أو أثناء Group C، أزلها إذا كانت ضمن نطاق C architecture/contracts.

لا تضف أي Safe Zone contract.

---

21. CONTRACT TESTS

يجب كتابة اختبارات للعقود التي يمكن اختبارها الآن.

على الأقل:

- Signal validation
- Signal strength values
- Event UUID uniqueness
- Event state validity
- location CURRENT vs LAST_KNOWN representation
- delivery status representation
- Authentication boundary behavior
- prohibited direct event creation paths إذا أمكن اختبارها معماريًا

لا تختبر Features لم يتم تنفيذها.

---

22. DOCUMENTATION

يجب تحديث/إنشاء:

Core Contract Inventory

يوضح:

- Contract
- Responsibility
- Owner layer
- Inputs
- Outputs
- Forbidden responsibilities
- Future phase

Boundary Map

يوضح:

"UI → Domain → Detection/Evidence/Delivery → Platform"

مع منع dependency direction الخاطئ.

State Transition Documentation

يوضح الحالات والعلاقات المسموحة.

---

23. TRACEABILITY

يجب تحديث:

"Requirement Traceability Matrix"

بالصيغة:

"| ID | Requirement | Source | Action / Decision | Evidence | Status | Notes |"

Status فقط:

- PASS
- FAIL
- PARTIAL
- BLOCKED
- NEEDS REVIEW

أي PASS يجب أن يملك evidence حقيقي.

لا تستخدم "VERIFIED".

---

24. ADR

إذا اتخذت قرارًا معماريًا حقيقيًا أثناء Group C، يجب تسجيل ADR.

الصيغة:

"ADR-XXX"

- Title
- Decision
- Context
- Options Considered
- Advantages
- Disadvantages
- Chosen Option
- Reason
- Consequences
- Evidence
- Review Status

Review Status فقط:

- PENDING EXTERNAL REVIEW
- APPROVED BY EXTERNAL REVIEWER
- REJECTED BY EXTERNAL REVIEWER
- DEFERRED

لا تدّعِ External Approval.

لا تنشئ ADRs مصطنعة.

---

25. BUILD & TEST

يجب تنفيذ فعليًا:

- tests
- compile/build
- static analysis المناسب

يجب تسجيل الأوامر والنتائج الحقيقية.

إذا فشل أي شيء، أصلحه إذا كان ضمن Group C.

إذا لم يمكن إصلاحه ضمن Group C، سجله بوضوح كـ BLOCKED/PARTIAL/NEEDS REVIEW.

---

26. GIT VERIFICATION

في النهاية يجب فحص:

- branch
- status
- changed files
- untracked files
- commit SHA
- remote
- push verification

لا تدّعي push بدون دليل.

---

27. SECOND SELF-AUDIT

يجب عليك إجراء Self-Audit مستقل بعد اكتمال التنفيذ.

تحقق خصوصًا من:

- لا Feature implementation.
- لا Legacy code.
- لا Safe Zones.
- لا Geofencing.
- لا continuous GPS.
- لا Camera implementation.
- لا delivery implementation.
- لا fake data.
- لا fake test claims.
- لا unauthorized dependencies.
- لا architecture bypass.
- UI لا يتجاوز Domain.
- Guards لا تنشئ Confirmed Events.
- ProtectionStateEngine هو السلطة الوحيدة معماريًا.
- لا production security claims.

إذا وجدت خطأ متعلقًا بـGroup C، أصلحه ثم أعد الاختبارات.

---

28. FINAL REPORT

بعد الانتهاء أرسل تقريرًا بهذا الشكل:

"GROUP: C — Core Contracts & Boundaries"

"STATUS:"

Micro-tasks executed

- ...

Micro-tasks not executed

- ...

Files changed

- ...

Files created

- ...

Dependencies changed

- ...

Contracts created/updated

- ...

Boundaries verified

- ...

Commands executed

- ...

Tests

- ...

Build

- ...

Static analysis

- ...

Git evidence

- ...

Decisions

- ...

ADRs

- ...

Traceability entries

- ...

Blockers

- ...

Limitations

- ...

Next proposed step

"AI DESIGNER / PHASE 3 — UX/UI DESIGN"

---

29. ABSOLUTE STOP

بعد Group C:

يجب عليك التوقف تمامًا.

ممنوع:

- Group D
- Phase 2
- Phase 3
- Features
- Final UI
- Camera
- Location
- Delivery
- Detection
- Billing
- Ads

الحالة النهائية:

"IMPLEMENTATION COMPLETE — AWAITING EXTERNAL REVIEW"

لا تستخدم "VERIFIED".

نفّذ Group C الآن فعليًا، ثم توقف وانتظر المراجعة.