# Balance Service

یک سرویس ساده برای مدیریت موجودی حساب‌ها که تمرکز اصلی آن روی **Concurrency، Idempotency و Consistency** در عملیات مالی است.

در این پروژه PostgreSQL منبع اصلی اطلاعات مالی است و تغییر موجودی، ثبت تراکنش و ثبت Event مربوط به آن داخل یک Transaction انجام می‌شود.

## Tech Stack

- Java 21
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Redis
- Apache Kafka
- JUnit 5
- Testcontainers

## Architecture

موجودی حساب در PostgreSQL نگهداری می‌شود و برای جلوگیری از تغییر هم‌زمان موجودی از `PESSIMISTIC_WRITE` استفاده شده است.

در یک عملیات موفق، این مراحل داخل یک Transaction انجام می‌شوند:

```text
Lock Account
    ↓
Check Transaction ID
    ↓
Update Balance
    ↓
Save Financial Transaction
    ↓
Save Outbox Event
    ↓
Commit
```

بنابراین اگر هر قسمت از عملیات با خطا مواجه شود، کل تغییرات Rollback می‌شوند.

PostgreSQL در این پروژه **Source of Truth** است.

---

## Concurrency

برای عملیات روی یک حساب، رکورد Account با `PESSIMISTIC_WRITE` قفل می‌شود.

مثلاً اگر دو درخواست هم‌زمان بخواهند موجودی حساب `A` را تغییر دهند:

```text
Request 1 → Account A → Lock
Request 2 → Account A → Wait
```

درخواست دوم بعد از آزاد شدن Lock می‌تواند ادامه دهد.

در نتیجه دو درخواست نمی‌توانند هم‌زمان یک Balance قدیمی را بخوانند و هر دو آن را تغییر دهند.

حساب‌های متفاوت به صورت مستقل Lock می‌شوند و روی یکدیگر تأثیری ندارند.

---

## Transfer

در Transfer باید هم حساب مبدا و هم مقصد Lock شوند.

برای جلوگیری از Deadlock، قبل از گرفتن Lock، ID دو حساب مرتب می‌شوند.

مثلاً این دو درخواست را در نظر بگیرید:

```text
A → B
B → A
```

هر دو در نهایت Lockها را با یک ترتیب می‌گیرند:

```text
A → B
```

به همین دلیل Deadlock ناشی از Lock Order ایجاد نمی‌شود.

انتقال از یک حساب به خودش هم مجاز نیست:

```text
A → A
```

---

## Idempotency

هر عملیات یک `transactionId` دارد.

`transaction_id` در دیتابیس Unique است، بنابراین یک Transaction نمی‌تواند بیشتر از یک بار اثر مالی داشته باشد.

برای حالتی که دو درخواست با یک `transactionId` به صورت هم‌زمان وارد شوند، از PostgreSQL Advisory Transaction Lock هم استفاده شده است.

این Lock باعث می‌شود درخواست‌های هم‌زمان برای یک Transaction ID پشت سر هم اجرا شوند؛ حتی زمانی که هنوز رکورد Transaction در دیتابیس ایجاد نشده است.

اگر همان `transactionId` دوباره با اطلاعات متفاوت استفاده شود، درخواست با خطای:

```text
TRANSACTION_CONFLICT
```

رد می‌شود.

---

## Redis

Redis برای سریع‌تر کردن بررسی Transactionهای اخیر استفاده می‌شود.

اما Redis بخشی از منطق اصلی Correctness نیست.

یعنی اگر Redis در دسترس نباشد:

```text
Redis ❌
PostgreSQL ✅
```

سیستم همچنان باید بتواند عملیات را به شکل صحیح انجام دهد.

به همین دلیل Redis فقط یک Fast Path / Cache است و اطلاعات اصلی در PostgreSQL قرار دارد.

---

## Outbox

Event مربوط به هر عملیات مالی در همان Transaction دیتابیس در جدول Outbox ذخیره می‌شود.

مثلاً:

```text
Account Update
Transaction Record
Outbox Event
```

همه داخل یک Transaction هستند.

بعد از Commit، یک Publisher Eventهای Pending را از Outbox می‌خواند و به Kafka ارسال می‌کند.

اگر Kafka در دسترس نباشد، عملیات مالی Rollback نمی‌شود و Event در Outbox باقی می‌ماند تا بعداً دوباره ارسال شود.

این روش مشکل Dual Write بین PostgreSQL و Kafka را حل می‌کند.

---

## Kafka

Kafka برای ارسال Eventها به Consumerها استفاده می‌شود.

Kafka مسئول نگهداری Balance نیست.

به صورت ساده:

```text
PostgreSQL → Financial State
Kafka      → Event Distribution
Redis      → Fast Path
```

Publisher به صورت **At-Least-Once** کار می‌کند.

بنابراین در شرایطی مثل Crash شدن Application بعد از ارسال پیام به Kafka، ممکن است یک Event بیشتر از یک بار ارسال شود.

---

## Inbox

برای جلوگیری از پردازش دوباره Eventها، Consumer از Inbox Pattern استفاده می‌کند.

هر Event یک `eventId` دارد و این مقدار در Inbox به صورت Unique ذخیره می‌شود.

اگر یک Event دوباره از Kafka دریافت شود، وجود آن در Inbox بررسی می‌شود و در صورت وجود، دوباره پردازش نمی‌شود.

```text
Kafka Event
    ↓
Check Inbox
    ↓
Already exists?
    ├── Yes → Ignore
    └── No  → Process
```

Unique Constraint دیتابیس هم آخرین لایه جلوگیری از پردازش هم‌زمان Duplicate Eventها است.

---

## Amount

مقدار Amount از نوع `long` است.

مقدار `long` نشان‌دهنده کوچک‌ترین واحد پولی سیستم است.

مثلاً:

```text
500
```

یعنی 500 واحد پایه پولی.

Amount باید بزرگ‌تر از صفر باشد.

از `double` استفاده نشده، چون برای محاسبات مالی مناسب نیست.

---

# API

## Credit

```http
POST /api/v1/balances/credit
```

```json
{
  "accountId": "A",
  "amount": 500,
  "transactionId": "TX-100"
}
```

---

## Debit

```http
POST /api/v1/balances/debit
```

```json
{
  "accountId": "A",
  "amount": 300,
  "transactionId": "TX-101"
}
```

اگر موجودی کافی نباشد، عملیات انجام نمی‌شود.

---

## Transfer

```http
POST /api/v1/balances/transfer
```

```json
{
  "sourceAccountId": "A",
  "destinationAccountId": "B",
  "amount": 300,
  "transactionId": "TX-102"
}
```

مبدا و مقصد باید متفاوت باشند.

---

## Get Balance

```http
GET /api/v1/balances/A
```

---

# Invariants

چند قانون اصلی در سیستم باید همیشه برقرار باشند:

### Balance نمی‌تواند منفی شود

```text
balance >= 0
```

### هر تغییر Balance باید یک Financial Transaction داشته باشد.

### هر Transaction ID فقط یک اثر مالی دارد.

### در Transfer مجموع موجودی دو حساب تغییر نمی‌کند.

مثلاً:

```text
A = 1000
B = 500

A → B
300
```

بعد از Transfer:

```text
A = 700
B = 800
```

مجموع قبل و بعد یکسان است:

```text
1000 + 500 = 700 + 800
1500       = 1500
```

---

# Tests

تست‌های پروژه شامل موارد زیر هستند:

### Basic Operations

- Credit
- Debit
- Insufficient Balance
- Unknown Account
- Same Account Transfer

### Idempotency

- Credit Idempotency
- Debit Idempotency
- Transfer Idempotency
- Concurrent Duplicate Requests

### Concurrency

- Concurrent Debits
- Concurrent Opposite Transfers

### Transfer

- Transfer Total Invariant

برای تست‌های Concurrency از PostgreSQL واقعی در Testcontainers استفاده شده است.

این موضوع مهم است، چون رفتارهایی مثل:

```text
PESSIMISTIC_WRITE
Row Lock
Transaction
Advisory Lock
```

وابسته به رفتار واقعی PostgreSQL هستند و Mock کردن آن‌ها تست مناسبی برای این قسمت نیست.

---

# Running

برای بالا آوردن Infrastructure:

```bash
docker compose up -d
```

اجرای Application:

```bash
./mvnw spring-boot:run
```

اجرای Testها:

```bash
./mvnw test
```

اگر Maven Wrapper داخل پروژه نباشد:

```bash
mvn test
```

---

# Design Decisions

## چرا Pessimistic Lock؟

چون در این سرویس موجودی مستقیماً با عملیات مالی تغییر می‌کند و مدل Pessimistic Lock برای کنترل Concurrent Update ساده و قابل پیش‌بینی است.

در عوض، روی حساب‌هایی که تعداد درخواست بسیار زیادی دارند، Contention می‌تواند باعث کاهش Throughput شود.

## چرا Advisory Lock؟

برای جلوگیری از Race Condition در Idempotency.

صرفاً این کار:

```java
existsByTransactionId(...)
```

برای درخواست‌های هم‌زمان کافی نیست، چون ممکن است هر دو درخواست قبل از ایجاد Transaction رکورد مورد نظر را پیدا نکنند.

Advisory Lock این قسمت را Serialize می‌کند.

## چرا Redis منبع اصلی نیست؟

چون از دست رفتن Redis نباید روی Correctness مالی تأثیر بگذارد.

Redis فقط برای سریع‌تر شدن بعضی بررسی‌ها استفاده می‌شود.

## چرا Outbox؟

برای اینکه تغییر دیتابیس و ارسال Event به Kafka دو عملیات مستقل نباشند.

ابتدا Event همراه با تغییرات مالی در PostgreSQL ذخیره می‌شود و بعداً توسط Publisher به Kafka ارسال می‌شود.

## چرا Inbox؟

چون Outbox Publisher و Kafka هر دو می‌توانند باعث دریافت Duplicate Event شوند.

Inbox باعث می‌شود Consumer بتواند این Eventها را به صورت Idempotent پردازش کند.

---

# خلاصه

در این پروژه مسئولیت‌ها به این شکل تقسیم شده‌اند:

```text
PostgreSQL
    ↓
Source of Truth
    ↓
Balance + Transactions + Outbox + Inbox

Redis
    ↓
Fast Path / Cache

Kafka
    ↓
Event Distribution
```

هدف اصلی طراحی این است که حتی در شرایطی مثل:

- درخواست‌های هم‌زمان
- Duplicate Request
- Duplicate Kafka Event
- Kafka Down
- Redis Down
- Application Crash

قوانین اصلی موجودی همچنان حفظ شوند.

به طور خلاصه:

```text
Correctness → PostgreSQL
Concurrency → Database Locks
Idempotency → Transaction ID + Advisory Lock
Events      → Outbox + Kafka
Consumers   → Inbox
Performance → Redis
Testing     → Testcontainers + PostgreSQL
```