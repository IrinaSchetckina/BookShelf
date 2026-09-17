# ReadShelf

Крос-платформний трекер книжок: пошук у публічному каталозі + власні полиці (Хочу прочитати / Читаю / Прочитано) з авторизацією. Наскрізний Kotlin: Ktor-бекенд + KMP + Compose Multiplatform (Android/iOS/Web).

## Модулі та відповідальність
> Звір із фактичними `build.gradle.kts` кожного модуля; якщо назви інші — поправ тут.

- `:core` — чистий Kotlin. Домен (`ua.readshelf.domain`: моделі, інтерфейси репозиторіїв, use-cases) і контракт нашого HTTP API (`ua.readshelf.contract`: `@Serializable` DTO), спільний для `:server` і клієнтів. Дозволені залежності: лише kotlinx-serialization, coroutines і kotlinx-datetime (дати дня читання — чиста календарна арифметика). Без Android/Ktor/Compose.
- `:app:shared` — спільний код клієнтів: presentation (ViewModels/стан), реалізації репозиторіїв, мережевий шар (Ktor Client), DI, спільний Compose UI.
- `:app:androidApp` — тонка Android-точка входу.
- `:app:webApp` — тонка Web точка входу; збирається під обидва таргети (Wasm і JS), спільний код у `webMain`.
- `app/iosApp` — Xcode-проєкт, точка входу iOS (не Gradle-модуль).
- `:server` — Ktor-бекенд: проксі до Open Library, авторизація, полиці, PostgreSQL.
  Авторизація: JWT, секрет із env `JWT_SECRET` (без нього сервер не стартує), паролі — BCrypt
  (хешування — у `Dispatchers.Default`, не на потоках запиту),
  користувачі поки що in-memory. Серверні типи з паролем (`UserRecord`, `UserRepository`) живуть
  у `:server`, бо `:core` компілюється в клієнти. CORS у проді — список origin-ів із
  `CORS_ALLOWED_ORIGINS`; тіла запитів на auth-роутах не логуємо (виняток розбору JSON цитує пароль).

## Технологічні рішення
- Мова: Kotlin, строго. Без `!!` та зайвих `any`-подібних обходів типів.
- HTTP: **Ktor Client** (клієнт) / **Ktor Server** (бекенд).
- Серіалізація: **kotlinx.serialization**. Усі DTO — `@Serializable`.
- Асинхрон: coroutines + Flow. Ніяких блокуючих викликів у UI/у suspend-контексті.
- DI: на `:server` — **Koin** (`serverModule()`, `Application.module(vararg overrides)`; тести
  підмінюють окремі визначення власним модулем). У `:app:shared` поки що ручний `AppContainer`.
- БД (сервер): PostgreSQL + **Exposed**.
- БД (клієнт): **SQLDelight** 2.1.0 у `:app:shared`, з `generateAsync = true` — `web-worker-driver`
  (js/wasmJs) існує лише асинхронним. Наслідок для всіх таргетів, включно з Android та iOS:
  згенерований API — **suspend скрізь** (`awaitAsList()`, `Schema.awaitCreate(driver)`);
  синхронних викликів БД немає, `runBlocking` як обхід не використовуємо.
  На вебі — власний воркер `readshelf-sqlite.worker.js` (`@sqlite.org/sqlite-wasm`, OPFS `opfs-sahpool`),
  а не sql.js зі SQLDelight: той тримає БД у пам'яті. Обмеження: одна вкладка застосунку за раз.
  Зовнішніх ключів із каскадом у схемі немає — пов'язані рядки видаляємо явно в транзакції.
- Публічне API: Open Library (`https://openlibrary.org`), без ключа. Клієнти ходять НЕ напряму в Open Library, а тільки через наш `:server`.

## Архітектурні правила
- Залежності односторонні: `core` нічого не знає про `shared`/платформи. UI → presentation → domain(`core`) → data.
- Доменні моделі (`ua.readshelf.domain`) відокремлені від DTO (`ua.readshelf.contract` — наш API, `data.remote` — зовнішні). Мапимо DTO ↔ domain явно.
- Жодних мережевих чи БД-викликів прямо з Compose-функцій.

## Неймінг
- Пакети: `ua.readshelf.<layer>` (напр. `ua.readshelf.data.remote`, `ua.readshelf.domain`).
- DTO — суфікс `Dto` (`BookDto`); domain — без суфікса (`Book`).
- Репозиторії, спільні з клієнтами: інтерфейс `BookRepository` у `core`, реалізація `BookRepositoryImpl` у `shared`.
  **Суто серверні — цілком у `:server`** (інтерфейс `UserRepository` і `InMemoryUserRepository` поруч,
  у `ua.readshelf.auth`): вони оперують хешем пароля, а `:core` компілюється в клієнти.

## Команди
- Бекенд: `JWT_SECRET=dev-secret ./gradlew :server:run` (localhost:8080; без секрету не стартує)
- Web: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
- Android: запуск із IDE (`:app:androidApp`)
- iOS: із Xcode/IDE (потрібен Xcode 26+)
- Тести: `./gradlew :server:test :core:jvmTest :app:shared:testAndroidHostTest`; уся збірка + перевірки всіх таргетів — `./gradlew build`
- Лінт/формат: `./gradlew ktlintCheck` (додамо в М6, якщо ще нема)

## Збірка
`:app:shared` має таргети iosArm64, iosSimulatorArm64, js, wasmJs, android — **jvm немає**,
тож задачі `:app:shared:jvmTest` не існує (спільні тести ганяємо через `testAndroidHostTest`).
У `:core` jvm-таргет є — `:core:jvmTest` існує (звірено `./gradlew :core:tasks --all`).
Залежності host-тестів `:app:shared` — лише через `getByName("androidHostTest").dependencies { }`:
типізованого акцесора `androidHostTest` у DSL AGP-KMP-плагіна немає (падає на конфігурації).
Лінкування iOS-фреймворків (Kotlin/Native) за замовчуванням іде **в процесі Gradle daemon**
(`kotlin.native.disableCompilerDaemon=false`), тож його пам'ять обмежує `org.gradle.jvmargs`,
а не `kotlin.daemon.jvmargs`. На 4 ГБ повна збірка падала з OutOfMemoryError у
`linkReleaseFramework*`; зараз 6 ГБ. Врахувати в CI (М8).
Браузерні тести (karma) на холодному старті під повною збіркою не вкладаються в стандартні
таймаути — вони підняті в `app/shared/karma.config.d/timeouts.js`.

## Робочий процес (5 фаз) — обовʼязково
Будь-яку нетривіальну задачу веди фазами, не змішуючи їх:
1. **Explore** — прочитати релевантний код, нічого не змінювати.
2. **Plan** — описати план (файли, кроки, перевірка) і дочекатися підтвердження.
3. **Implement** — робити строго за планом.
4. **Verify** — запустити тести/збірку, перечитати дифи.
5. **Commit** — атомарний коміт із чітким описом.
Правило: у фазі Explore/Plan **не** редагувати файли.

## Чого НЕ робити
- Не ходити з клієнтів напряму в Open Library в обхід `:server`.
- Не додавати залежності в `:core` на Android/Ktor/Compose.
- Не комітити `local.properties`, `build/`, секрети.
- Не пушити напряму в `main` без потреби (працюємо через гілки).
- Не роздувати цей файл: процедурні деталі виносимо в Skills (Модуль 2).
