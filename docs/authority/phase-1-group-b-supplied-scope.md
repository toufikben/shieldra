يجب عليك القيام بـ التنفيذ الفعلي الآن داخل مستودع GitHub:

"https://github.com/toufikben/shieldra"

المهمة الوحيدة

GROUP B — ARCHITECTURE & PROJECT STRUCTURE

لا أريد تقريرًا نظريًا أو اقتراحات فقط.

يجب عليك تعديل المستودع فعليًا وإنشاء الأساس البرمجي المطلوب لـGroup B.

---

1. لا تتوقف بسبب Authority Files

لا تتوقف لأن "docs/authority/" غير موجودة.

المواصفات التنفيذية الموجودة في هذه المهمة هي مصدر التنفيذ الحالي، ومعها Product Freeze v5 والقواعد المثبتة في هذه المهمة.

يجب عليك إنشاء:

docs/authority/

وحفظ وثائق Phase 1 المتاحة فعليًا هناك.

إذا كان جزء من Authority غير متاح نصيًا لك، لا تخترعه، ولكن لا تجعل ذلك سببًا لعدم تنفيذ الأجزاء المعمارية التي يمكن تنفيذها بثقة ووضوح من المواصفات الحالية.

أي قرار غير محسوم يجب تسجيله كـ:

"NEEDS REVIEW"

ولا يجوز اختراعه.

---

2. المشروع من الصفر

يجب عليك القيام ببناء المشروع الحالي من الصفر.

ممنوع تمامًا استخدام:

"phone_fortess_v2_pro"

أو أي مشروع قديم.

لا:

- inspect
- copy
- migrate
- reuse
- compare
- import

من المشروع القديم.

---

3. افحص المستودع أولًا

قبل التعديل:

git status
git branch --show-current
git remote -v
find . -maxdepth 4 -type f | sort

افحص Group A الموجود فعليًا.

لا تعيد Group A.

لا تحذف الأدلة الموجودة.

---

4. افحص Skills

يجب عليك القيام بفحص الـskills المتاحة واستخدام الـskills المرتبطة مباشرة بـ:

- Android
- Kotlin
- Gradle
- architecture
- project structure
- dependency management
- security architecture
- testing
- static analysis
- CI/CD

لا تستخدم Skill غير ضرورية.

الـSkills لا تتجاوز المواصفات.

---

5. Group B Architecture

يجب عليك إنشاء أساس معماري واضح وقابل للتوسع.

اعتمد هذه الحدود:

UI

Domain
 ├── ProtectionStateEngine
 ├── EventPipeline
 ├── DeliveryOrchestrator
 └── AuthenticationGate

Detection
 ├── LockGuard
 ├── MotionGuard
 ├── SimGuard
 ├── BatteryGuard
 └── PanicGuard

Evidence
 ├── CaptureManager
 ├── EvidenceValidator
 └── EncryptedVault

Delivery
 ├── SmartQueue
 └── ChannelAdapters

Platform
 ├── ForegroundServices
 ├── WorkManager
 ├── Android Keystore
 └── Room

هذه حدود معمارية في Group B وليست دعوة لتنفيذ الميزات.

---

6. ممنوع Feature Implementation

يجب عليك القيام بالتأكد أن Group B لا ينفذ:

- Security Engine behavior
- ProtectionStateEngine behavior
- Lock detection
- Failed Unlock
- Motion detection
- SIM detection
- Panic behavior
- Battery Emergency
- Camera
- Evidence capture
- Location collection
- Delivery
- SMTP
- WhatsApp
- Telegram
- Smart Queue behavior
- Automatic Recovery
- Notifications production flow
- Billing
- Ads SDK
- Safe Zones
- Geofencing
- continuous GPS
- production encryption
- production authentication
- final UI

يمكن إنشاء interfaces/contracts/foundations فقط عندما تكون ضرورية للمعمارية.

لا fake implementations.

---

7. Dependency Direction

يجب عليك القيام بتصميم dependency direction واضح.

القاعدة:

UI
 ↓
Domain
 ↓
Contracts / abstractions
 ↓
Platform implementations

ولا تسمح بأن:

- UI يحتوي business rules.
- Detection يعتمد على UI.
- Domain يعتمد على Android UI.
- Delivery يقرر security state.
- Evidence يقرر security state.
- Platform يقرر business rules.

يجب منع circular dependencies.

---

8. ProtectionStateEngine

أنشئ فقط boundary مناسبًا لـ:

"ProtectionStateEngine"

ولا تنفذ داخله detection/security confirmation.

المبدأ المعماري:

ProtectionStateEngine هو السلطة المستقبلية الوحيدة لإنشاء CONFIRMED_SECURITY_EVENT.

لكن Group B لا ينفذ هذا السلوك.

---

9. Guards

أنشئ الحدود المعمارية فقط لـ:

- LockGuard
- MotionGuard
- SimGuard
- BatteryGuard
- PanicGuard

كل Guard مستقبلًا ينتج Signal.

لا تنفذ detection logic الآن.

---

10. Signal foundation

إذا كان إنشاء contract ضروريًا، استخدم:

Signal
- type
- strength
- timestamp
- context

والقوة:

STRONG
MEDIUM
WEAK

لا تضف rules أو thresholds أو detection behavior في Group B.

---

11. Event foundation

أنشئ boundary مناسبًا لـEventPipeline دون تنفيذ event detection أو delivery.

الـfuture lifecycle:

INITIATED
→ COLLECTING
→ READY
→ DELIVERING
→ DELIVERED
   / DEFERRED
   / FAILED_FINAL

"EXPIRED" retention terminal state.

لا تنفذ lifecycle engine كاملًا في Group B إلا إذا كان ضروريًا للعقد المعماري.

---

12. Evidence boundary

أنشئ فقط boundaries:

- CaptureManager
- EvidenceValidator
- EncryptedVault

لا تنفذ:

- camera
- location collection
- production encryption
- evidence capture

---

13. Delivery boundary

أنشئ فقط boundaries:

- DeliveryOrchestrator
- SmartQueue
- ChannelAdapters

لا تنفذ delivery.

لا تربط Detection مباشرة بأي:

- SMTP
- WhatsApp
- Telegram

---

14. Authentication boundary

أنشئ:

"AuthenticationGate"

كحد معماري فقط.

لا تنفذ production authentication.

لا fake password.

---

15. Persistence boundary

جهز architecture مناسبة مستقبلًا لـRoom، لكن لا تنفذ production database schema.

ممنوع إنشاء أي persistence لـ:

- Safe Zones
- Geofencing
- continuous GPS

---

16. Android platform boundary

جهز الحدود المستقبلية لـ:

- Foreground Services
- WorkManager
- Android Keystore
- Room

لكن لا تدّعي أن functionality production-ready.

---

17. Project Structure

يجب عليك اختيار structure حقيقي وبسيط وقابل للصيانة.

لا تنشئ عشرات modules بلا داعٍ.

أنشئ فقط ما له قيمة معمارية واضحة.

يجب أن تنتج وثيقة:

docs/architecture/project-structure.md

تحتوي على:

- module/package tree
- responsibility لكل package/module
- dependency direction
- forbidden dependencies
- boundaries

---

18. ADR

إذا اتخذت قرارًا معماريًا حقيقيًا، يجب عليك إنشاء ADR.

المكان:

docs/architecture/adr/

الصيغة:

ADR-XXX
Title:
Decision:
Context:
Options Considered:
Advantages:
Disadvantages:
Chosen Option:
Reason:
Consequences:
Evidence:
Review Status:

Review Status:

"PENDING EXTERNAL REVIEW"

لا تستخدم:

"VERIFIED"

ولا تدّعي External Approval.

لا تنشئ ADR مصطنعًا إذا لم يكن هناك قرار يحتاجه.

---

19. Dependency Inventory

يجب عليك إنشاء:

docs/architecture/dependencies.md

لكل dependency:

Name
Version
Purpose
License
Maintenance
Security
Required/Optional

لا تضف dependency غير ضرورية.

---

20. Traceability

أنشئ:

docs/traceability/phase-1-traceability.md

بالصيغة:

| ID | Requirement | Source | Action / Decision | Evidence | Status | Notes |

لا تستخدم VERIFIED.

إذا لم يوجد evidence:

Evidence = NONE

ولا تجعل Status = PASS في هذه الحالة.

---

21. Code Quality

بعد التنفيذ يجب عليك القيام بفحص فعلي للمشروع بحثًا عن:

TODO
FIXME
placeholder
fake success
mock
empty methods
dead code
duplicate logic
swallowed exceptions
hardcoded secrets
sensitive logs
unnecessary dependencies
architecture violations

أصلح المشاكل التي تخص Group B.

لا تدخل في Group C أو المراحل اللاحقة لإصلاح مشاكل خارج النطاق.

---

22. Verification

يجب عليك القيام بتنفيذ التحقق الفعلي المناسب.

على الأقل:

git status
git diff --check

ثم استخدم أوامر Gradle المناسبة للمشروع بعد إنشائه/تهيئته.

إذا كان هناك Android project قابل للبناء، يجب تشغيل compile/build مناسب.

إذا لم يكن build مطلوبًا بعد بسبب حدود Group B، سجّل ذلك بوضوح ولا تزعم نجاح build.

---

23. Git

في النهاية يجب عليك القيام بفحص:

git status
git diff --stat
git diff --check
git log -1 --oneline
git remote -v

وسجّل:

- branch
- status
- changed files
- untracked files
- commit SHA إن وجد
- remote
- push verification

لا تدّعي push إذا لم تتحقق منه.

---

24. التقرير الإلزامي

أنشئ:

docs/reports/group-b-report.md

ويجب أن يحتوي:

GROUP:
Group B — Architecture & Project Structure

STATUS:
PASS / PARTIAL / FAIL / BLOCKED / NEEDS REVIEW

Micro-tasks executed:
- ...

Micro-tasks not executed:
- ...

Files changed:
- ...

Files created:
- ...

Dependencies changed:
- ...

Commands executed:
- ...

Tests:
- ...

Build:
- ...

Static analysis:
- ...

Git evidence:
- Branch:
- Status:
- Changed files:
- Untracked files:
- Commit SHA:
- Remote:
- Push verification:

Decisions:
- ...

ADRs:
- ...

Traceability entries:
- ...

Out-of-scope checks:
- ...

Limitations:
- ...

Blockers:
- ...

Next proposed group:
Group C — Core Contracts & Boundaries

Final state:
IMPLEMENTATION COMPLETE — AWAITING EXTERNAL REVIEW

---

25. Second Self-Audit

بعد إتمام التنفيذ يجب عليك القيام بمراجعة ثانية مستقلة للمستودع الفعلي.

لا تعتمد على ملخص التنفيذ.

أعد فحص:

- architecture
- package boundaries
- dependency direction
- forbidden features
- removed Safe Zone architecture
- code quality
- dependencies
- ADR
- traceability
- Git
- evidence

---

26. STOP RULE

هذه أهم قاعدة:

بعد Group B يجب عليك التوقف.

ممنوع البدء في:

- Group C
- Group D
- Group E
- Group F
- Group G
- Group H

ممنوع إضافة تحسينات غير مطلوبة.

ممنوع إعادة التصميم.

ممنوع تنفيذ features مستقبلية.

---

27. قاعدة التنفيذ

لا أريد منك أن تقول:

"يمكنني فعل ذلك."

يجب عليك القيام به فعليًا.

لا أريد:

"يُفترض أن يعمل."

أريد نتيجة command فعلية.

لا أريد:

"تم الدفع إلى GitHub."

إلا بعد التحقق من remote.

لا أريد:

"Verified."

أنت لست External Reviewer.

---

ابدأ الآن

نفّذ Group B فقط داخل "toufikben/shieldra".

ابدأ بفحص repository، ثم نفذ micro-tasks، ثم verification، ثم التقرير.

لا تنتظر ملفات إضافية.
لا تعيد Group A.
لا تستخدم المشروع القديم.
لا تبدأ Group C.

بعد إتمام Group B، توقف وانتظر المراجعة الخارجية.