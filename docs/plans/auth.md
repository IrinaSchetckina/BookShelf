# План: серверна авторизація (register / login / JWT + захищений `/me`)

> Живий план для агентів. Читати разом з `AGENTS.md` і скілом `write-tests`.
> Робота — строго за 5-фазним процесом.
> Статус: **Explore + Plan — на погодження.** Код не пишемо, поки план не підтверджено.

## Скоуп

**У скоупі:** тільки `:server` + контракт у `:core`.
`POST /auth/register`, `POST /auth/login`, `GET /me` (захищений), видача та перевірка JWT,
in-memory сховище користувачів, хешування паролів BCrypt, Koin як DI на бекенді.

**Поза скоупом:** будь-який клієнтський код (`:app:*`) — екрани логіну, зберігання токена,
`Authorization`-хедер у `ReadShelfApi`; PostgreSQL/Exposed; refresh-токени, logout, «запам'ятати мене»,
скидання пароля, верифікація email, ролі/права, rate limiting, полиці. Міграція `AppContainer`
у `:app:shared` на Koin — теж поза скоупом (там DI лишається ручним).

## Рішення (погоджені користувачем)

- **`UserRepository` — in-memory.** Жодної БД у цій фічі. Реалізація тримає користувачів у мапі
  за нормалізованим email під `Mutex` (сервер багатопотоковий, `HashMap` без синхронізації — гонка).
  Інтерфейс пишемо так, щоб заміна на Exposed-реалізацію не зачепила роути.
- **Паролі — BCrypt** (`at.favre.lib:bcrypt`, чиста Java, без Spring). У домені й на дроті —
  ніколи не зберігаємо й не віддаємо пароль чи хеш.
- **JWT із секретом з env.** Секрет читаємо з `JWT_SECRET`, у код не хардкодимо й у git не кладемо.
- **DI — Koin** (`koin-ktor`). Це перше застосування Koin у проєкті; `AGENTS.md` («Koin — з М2»)
  після впровадження треба поправити в частині `:server`.

## Рішення, які пропоную (потребують підтвердження)

1. **`UserRepository` живе в `:server`, не в `:core`.**
   Неймінг у `AGENTS.md` («інтерфейс репозиторію в `core`») стосується репозиторіїв, спільних із клієнтами.
   Тут інтерфейс оперує хешем пароля — тобто серверною таємницею, — і жоден клієнт його не викликає.
   Класти його в `:core` означає віддати клієнтам (Android/iOS/Web) сигнатуру, у якій фігурує `passwordHash`.
   Тому: `ua.readshelf.auth.UserRepository` у `:server`.
   У `:core` з домену йде тільки `User(id, email)` — без хеша.
2. **`Application.module()` міняє сигнатуру.**
   Зараз тестованість тримається на `module(openLibraryClient: OpenLibraryClient = OpenLibraryClient())`.
   З Koin залежностей стане більше (репозиторій, хешер, JWT-сервіс, конфіг), і додавати їх параметрами —
   це той самий ручний DI, лише довший. Замість цього:
   ```kotlin
   fun Application.module(vararg overrides: Module) {
       install(Koin) { slf4jLogger(); modules(serverModule(environment), *overrides) }
       ...
   }
   ```
   Тест підміняє рівно те, що йому треба: `module(module { single { mockOpenLibraryClient(engine) } })`.
   Наслідок: `ApplicationTest` і `SearchRoutesTest` треба оновити — це частина цієї задачі, не окремої.
3. **`JWT_SECRET` відсутній → сервер не стартує** з явним повідомленням, а не мовчки бере дефолт.
   Дефолтний секрет у коді — це продакшн-інцидент, який ніхто не помічає, доки токени не почнуть підробляти.
   Ціна: локальний запуск стає `JWT_SECRET=dev-secret ./gradlew :server:run` — задокументуємо в README.
   У тестах конфіг передаємо явно, env не чіпаємо.
4. **Тривалість токена — 24 години**, `issuer`/`audience` — константи з дефолтами, перекриваються env.
   Refresh-токенів немає (поза скоупом), тож коротший TTL зробив би застосунок непридатним.

## Контракт HTTP

| Метод | Шлях | Тіло | Успіх | Помилки |
|---|---|---|---|---|
| POST | `/auth/register` | `RegisterRequestDto(email, password)` | `201` + `AuthResponseDto` | `400` невалідний email / короткий пароль; `409` email зайнятий |
| POST | `/auth/login` | `LoginRequestDto(email, password)` | `200` + `AuthResponseDto` | `400` порожнє поле; `401` невірні дані |
| GET | `/me` | — | `200` + `UserDto` | `401` без токена / невалідний / прострочений |

- Помилки віддаємо наявним `ErrorResponseDto(message)` — окремий формат не заводимо.
- **`401` для логіну не розрізняє «немає такого email» і «невірний пароль»** — однакове повідомлення
  `"Invalid email or password"`, інакше ендпоінт стає оракулом для перебору зареєстрованих адрес.
- Валідація: email — `trim()` + `lowercase()`, проста перевірка на `@` й крапку в домені
  (сувора RFC-регулярка тут шкідлива, вона відкидає валідні адреси); пароль — мінімум 8 символів,
  максимум 72 байти (**жорсткий ліміт самого BCrypt** — довші мовчки обрізаються).
- Реєстрація одразу повертає токен: інакше клієнту доведеться робити другий запит на логін.

## Файли

### `:core` — контракт і домен
- `core/src/commonMain/kotlin/ua/readshelf/contract/AuthDto.kt` *(новий)*
  `@Serializable RegisterRequestDto(email, password)`, `LoginRequestDto(email, password)`,
  `UserDto(id, email)`, `AuthResponseDto(token, user: UserDto)`.
- `core/src/commonMain/kotlin/ua/readshelf/domain/User.kt` *(новий)*
  `data class User(id: String, email: String)` — без хеша, без дати.

### `:server` — авторизація
- `server/.../auth/AuthConfig.kt` *(новий)* — `data class AuthConfig(secret, issuer, audience, realm, tokenLifetime)`
  + `fun authConfigFromEnv(): AuthConfig`, який кидає зрозумілий виняток за відсутнього `JWT_SECRET`.
- `server/.../auth/PasswordHasher.kt` *(новий)* — інтерфейс `hash(raw): String` / `verify(raw, hash): Boolean`
  + `BcryptPasswordHasher(cost: Int = 10)`. Інтерфейс потрібен саме для тестів: BCrypt із бойовим cost
  робить кожен тест на сотні мілісекунд повільнішим.
- `server/.../auth/JwtService.kt` *(новий)* — `issueToken(user): String`, `verifier: JWTVerifier`,
  клейм `userId` (`sub` лишаємо під email). Побудований на `com.auth0:java-jwt` із ktor-auth-jwt.
- `server/.../auth/UserRepository.kt` *(новий)* — інтерфейс: `findByEmail(email): UserRecord?`,
  `create(email, passwordHash): UserRecord`, `findById(id): UserRecord?`;
  `data class UserRecord(id, email, passwordHash)` — серверний тип, у `:core` не потрапляє.
- `server/.../auth/InMemoryUserRepository.kt` *(новий)* — мапа + `Mutex`, id через `UUID.randomUUID()`,
  email як ключ уже нормалізований.
- `server/.../auth/AuthService.kt` *(новий)* — `register(email, password)`, `login(email, password)`;
  уся валідація й нормалізація тут, роут лишається тонким. Помилки — sealed-результат
  або типізовані винятки (`EmailAlreadyTaken`, `InvalidCredentials`, `ValidationFailed`),
  роут мапить їх у статуси.
- `server/.../plugins/Security.kt` *(новий)* — `install(Authentication) { jwt("auth-jwt") { ... } }`,
  `validate` дістає `userId` і повертає принципала; `challenge` віддає `401` + `ErrorResponseDto`
  (дефолтний челендж Ktor віддає порожнє тіло — клієнту нема що показати).
- `server/.../routes/AuthRoutes.kt` *(новий)* — `authRoutes(authService)`;
  `meRoute(userRepository)` під `authenticate("auth-jwt")`.
- `server/.../di/ServerModule.kt` *(новий)* — Koin-модуль: `single<OpenLibraryClient>`, `single<AuthConfig>`,
  `single<PasswordHasher>`, `single<UserRepository>`, `single<JwtService>`, `single<AuthService>`.
- `server/.../Application.kt` *(правка)* — `install(Koin)`, `configureSecurity()`, реєстрація нових роутів,
  `searchRoutes` бере клієнта з Koin (`get()`); наявний `GET /` не чіпаємо.
- `server/.../plugins/Cors.kt` *(правка)* — **обов'язково**: додати `allowMethod(HttpMethod.Post)`,
  `allowHeader(HttpHeaders.Authorization)`, `allowHeader(HttpHeaders.ContentType)`.
  Без цього веб-клієнт із дев-сервера отримає CORS-помилку на кожен логін, і це виглядатиме як баг авторизації.

### Збірка
- `gradle/libs.versions.toml` — версії `koin`, `bcrypt`; бібліотеки `ktor-serverAuth`, `ktor-serverAuthJwt`
  (за наявним `ktor`-ref), `koin-ktor`, `koin-loggerSlf4j`, `bcrypt`.
- `server/build.gradle.kts` — ті самі залежності в `implementation`.
- Точні версії Koin/bcrypt фіксуємо на початку Implement після перевірки резолву — у плані їх не вгадуємо.

### Документація
- `README.md` — рядок про `JWT_SECRET` при `:server:run`.
- `AGENTS.md` — уточнити пункт про DI: на `:server` — Koin, у `:app:shared` поки що `AppContainer`.

## Кроки

1. **Збірка:** версії й залежності (`ktor-server-auth`, `ktor-server-auth-jwt`, `koin-ktor`, `bcrypt`),
   `./gradlew :server:compileKotlin` — переконатися, що все резолвиться, ще до написання логіки.
2. **`:core`:** `AuthDto.kt` + `domain/User.kt`. `./gradlew :core:build`.
3. **Koin без авторизації:** `di/ServerModule.kt` з єдиним `OpenLibraryClient`, нова сигнатура
   `Application.module(vararg overrides)`, оновлені `ApplicationTest` і `SearchRoutesTest`.
   `./gradlew :server:test` має бути **зеленим до того**, як з'явиться будь-який код авторизації —
   інакше не буде видно, що саме зламалося.
4. **Хешування й сховище:** `PasswordHasher`, `UserRepository`, `InMemoryUserRepository` + їхні тести.
5. **JWT:** `AuthConfig`, `JwtService`, `plugins/Security.kt` + тест на раунд-трип і чужий секрет.
6. **Сервіс і роути:** `AuthService`, `AuthRoutes` (register/login/me), реєстрація в `Application.kt`, CORS.
7. **Тести роутів:** `AuthRoutesTest` — повний набір із розділу Verify.
8. **Документація:** README + AGENTS.md.

## Verify

Тести (за скілом `write-tests`: `kotlin.test`, `testApplication`, `MockEngine`, рукописні фейки, без мок-бібліотек):

- `AuthRoutesTest`
  - `registers a new user and returns a token` → 201, тіло парситься в `AuthResponseDto`, `token` не порожній, email нормалізований;
  - `rejects a duplicate email with 409` — і для іншого регістру (`USER@x.com` після `user@x.com`);
  - `rejects an invalid email with 400`, `rejects a short password with 400`;
  - `logs in with valid credentials`, `rejects a wrong password with 401`, `rejects an unknown email with 401`;
  - `returns the current user for a valid token` → 200 + `UserDto`;
  - `rejects /me without a token with 401`, `rejects /me with a malformed token with 401`;
  - `does not return the password hash` — асерт на сирому `bodyAsText()`, що там немає підрядка хеша.
- `JwtServiceTest` — токен верифікується власним верифаєром; токен, підписаний іншим секретом, відхиляється.
- `InMemoryUserRepositoryTest` — email унікальний без урахування регістру й пробілів; `findById` після `create`.
- `PasswordHasherTest` — `hash` не дорівнює паролю, `verify` true на правильному й false на невірному
  (на `BcryptPasswordHasher(cost = 4)`, щоб не гальмувати прогін).
- Тести використовують `AuthConfig` з тестовим секретом і фейковий/дешевий хешер — `JWT_SECRET` з env не читають.

Команди:
```
./gradlew :core:build
./gradlew :server:test
./gradlew build
JWT_SECRET=dev-secret ./gradlew :server:run
curl -i -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"password1"}'
curl -i -X POST localhost:8080/auth/login    -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"password1"}'
curl -i localhost:8080/me -H "Authorization: Bearer $TOKEN"
curl -i localhost:8080/me                       # 401
curl -i "localhost:8080/search?q=dune&limit=3"  # не зламали наявний роут
```
Готово — тільки після зеленого прогону, не після написання коду.

## Commit

Гілка `feature/auth` (від `main`; поточна `feature/book-search` — про інше). Атомарні коміти:
1. `build: add ktor auth, koin and bcrypt dependencies`
2. `feat(core): add auth wire contract and user domain model`
3. `refactor(server): wire dependencies through koin` *(разом з оновленими наявними тестами)*
4. `feat(server): add in-memory user storage and bcrypt hashing`
5. `feat(server): issue and verify jwt tokens`
6. `feat(server): add register, login and protected /me routes`
7. `docs: document JWT_SECRET and the server DI choice`

`local.properties`, `build/`, будь-які реальні секрети — не комітимо. `JWT_SECRET` у git не потрапляє.

## Ризики й пастки

- **CORS.** Наявний конфіг дозволяє тільки `GET` і не дозволяє `Authorization` — веб-клієнт впаде на логіні.
- **72 байти BCrypt.** Довший пароль мовчки обрізається; тому явний ліміт у валідації.
- **`Mutex` в in-memory сховищі.** «Перевірив, що email вільний → створив» без блокування дає
  двох користувачів з одним email під паралельними запитами.
- **Секрет у логах.** `AuthConfig` не логуємо цілком; якщо додаємо `toString`, секрет маскуємо.
- **Дані живуть лише до рестарту** — це очікувано для in-memory, але треба сказати в README,
  щоб потім не шукали баг «зникли користувачі».
