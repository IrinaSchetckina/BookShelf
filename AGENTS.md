# ReadShelf

Крос-платформний трекер книжок: пошук у публічному каталозі + власні полиці (Хочу прочитати / Читаю / Прочитано) з авторизацією. Наскрізний Kotlin: Ktor-бекенд + KMP + Compose Multiplatform (Android/iOS/Web).

## Модулі та відповідальність
> Звір із фактичними `build.gradle.kts` кожного модуля; якщо назви інші — поправ тут.

- `:core` — чистий Kotlin. Доменні моделі, інтерфейси репозиторіїв, use-cases. Без Android/Ktor/Compose залежностей.
- `:app:shared` — спільний код клієнтів: presentation (ViewModels/стан), реалізації репозиторіїв, мережевий шар (Ktor Client), DI, спільний Compose UI.
- `:app:androidApp` — тонка Android-точка входу.
- `:app:webApp` — тонка Web (Wasm) точка входу.
- `iosApp` — Xcode-проєкт, точка входу iOS.
- `:server` — Ktor-бекенд: проксі до Open Library, авторизація, полиці, PostgreSQL.

## Технологічні рішення
- Мова: Kotlin, строго. Без `!!` та зайвих `any`-подібних обходів типів.
- HTTP: **Ktor Client** (клієнт) / **Ktor Server** (бекенд).
- Серіалізація: **kotlinx.serialization**. Усі DTO — `@Serializable`.
- Асинхрон: coroutines + Flow. Ніяких блокуючих викликів у UI/у suspend-контексті.
- DI: **Koin**.
- БД (сервер): PostgreSQL + **Exposed**.
- Публічне API: Open Library (`https://openlibrary.org`), без ключа. Клієнти ходять НЕ напряму в Open Library, а тільки через наш `:server`.

## Архітектурні правила
- Залежності односторонні: `core` нічого не знає про `shared`/платформи. UI → presentation → domain(`core`) → data.
- Доменні моделі (`core`) відокремлені від DTO (мережеві). Мапимо DTO ↔ domain явно.
- Жодних мережевих чи БД-викликів прямо з Compose-функцій.

## Неймінг
- Пакети: `ua.readshelf.<layer>` (напр. `ua.readshelf.data.remote`, `ua.readshelf.domain`).
- DTO — суфікс `Dto` (`BookDto`); domain — без суфікса (`Book`).
- Репозиторії: інтерфейс `BookRepository` у `core`, реалізація `BookRepositoryImpl` у `shared`.

## Команди
- Бекенд: `./gradlew :server:run` (localhost:8080)
- Web: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
- Android: запуск із IDE (`:app:androidApp`)
- iOS: із Xcode/IDE (потрібен Xcode 26+)
- Усі тести: `./gradlew test`
- Лінт/формат: `./gradlew ktlintCheck` (додамо в М6, якщо ще нема)

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
