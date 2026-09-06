# План: серверна авторизація (register / login / JWT + захищений `/me`)

> Живий план для агентів. Читати разом з `AGENTS.md` і скілом `write-tests`.
> Робота — строго за 5-фазним процесом.
> Статус: **реалізовано й відрев'ю́вано** на гілці `feature/auth` (PR #2).
> Розділ «Рішення, які пропоную» погоджено без змін. Те, що змінилося вже після написання
> плану, зібрано в розділі «Змінено після рев'ю» — цей документ описує код як він є, а не як задумувався.

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

## Рішення, які пропонувалися (усі погоджені)

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
       install(Koin) { slf4jLogger(); modules(serverModule(), *overrides) }
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

## Koin: перевірена семантика (не з документації, а з байткоду 4.2.2)

- **Перевизначення дозволене за замовчуванням.** У конструкторі `KoinApplication` поле
  `allowOverride` ініціалізується `true`, тож `modules(serverModule(), *overrides)` дає останньому
  модулю перекрити `single` з попереднього. Це саме те, на чому тримаються тести; якщо колись
  хтось викличе `strictOverride()`, весь тестовий набір впаде відразу й помітно.
- **`install(Koin)` — це глобальний контекст, не ізольований.** Плагін `org.koin.ktor.plugin.Koin`
  всередині викликає `startKoin` і `stopKoin` на зупинці застосунку. Наслідок: два Ktor-застосунки
  **не можуть жити одночасно в одній JVM** — буде `KoinApplicationAlreadyStarted`. Послідовні
  `testApplication` (як зараз) безпечні, бо кожен зупиняє свій застосунок.
  Тому **не запускати тести `:server` паралельно в межах однієї JVM** (форки JVM — можна).
- Ізольований плагін `KoinIsolated` у 4.2.2 **позначений `Deprecated`**, тож переходити на нього
  як на «правильніший» варіант не треба — підтримуваний шлях саме `install(Koin)`.

## Контракт HTTP

| Метод | Шлях | Тіло | Успіх | Помилки |
|---|---|---|---|---|
| POST | `/auth/register` | `RegisterRequestDto(email, password)` | `201` + `AuthResponseDto` | `400` невалідний email / короткий пароль; `409` email зайнятий |
| POST | `/auth/login` | `LoginRequestDto(email, password)` | `200` + `AuthResponseDto` | `400` порожнє поле; `401` невірні дані |
| GET | `/me` | — | `200` + `UserDto` | `401` без токена / невалідний / прострочений / запис зник |

Обидва POST-и додатково віддають `415`, якщо тіло надіслано не як `application/json`, і `400` на
тіло, що не лягає в DTO. Обидва статуси йдуть із `ErrorResponseDto`: власний `StatusPages` заведено
саме тому, що дефолтні відповіді Ktor тут — 500 і сирий рядок з іменем внутрішнього класу.
`401` завжди несе заголовок `WWW-Authenticate: Bearer realm=...` — його вимагає RFC 9110, і власний
`challenge` його втрачає, якщо не поставити руками.

- Помилки віддаємо наявним `ErrorResponseDto(message)` — окремий формат не заводимо.
- **`401` для логіну не розрізняє «немає такого email» і «невірний пароль»** — однакове повідомлення
  `"Invalid email or password"`, інакше ендпоінт стає оракулом для перебору зареєстрованих адрес.
- Валідація: email — `trim()` + `lowercase()`, проста перевірка на `@` й крапку в домені
  (сувора RFC-регулярка тут шкідлива, вона відкидає валідні адреси); пароль — мінімум 8 символів,
  максимум 72 байти (**жорсткий ліміт самого BCrypt** — довші мовчки обрізаються).
- Реєстрація одразу повертає токен: інакше клієнту доведеться робити другий запит на логін.
- **Тайминг логіну вирівняний.** Однакового повідомлення на `401` недостатньо: якби для невідомої
  адреси BCrypt-перевірка просто не виконувалась, відповідь приходила б помітно швидше, і цей час
  видавав би наявність адреси так само чітко, як окремий текст помилки. Тому `login` завжди робить
  рівно один `verify`: для невідомої адреси — проти хеша-заглушки `absentUserHash`, який рахується
  один раз на старті **тим самим налаштованим хешером**, щоб cost-фактор збігався з реальними хешами.
  Заглушку не хардкодимо рядком: хардкоджений хеш має фіксований cost і після зміни налаштувань
  почав би рахуватися інший час.

## Змінні оточення

| Змінна | Обов'язкова | Без неї |
|---|---|---|
| `JWT_SECRET` | так | сервер не стартує |
| `JWT_ISSUER` / `JWT_AUDIENCE` | ні | дефолти `readshelf` / `readshelf-clients` |
| `CORS_ALLOWED_ORIGINS` | у проді так | дев-режим: пускається лише localhost на будь-якому порту |

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
  робить кожен тест на сотні мілісекунд повільнішим. Функції лишаються блокуючими; за перенесення
  роботи з потоків запиту відповідає `AuthService`.
- `server/.../auth/JwtService.kt` *(новий)* — `issueToken(user): String`, `verifier: JWTVerifier`,
  клейм `userId` (`sub` лишаємо під email). Побудований на `com.auth0:java-jwt` із ktor-auth-jwt.
- `server/.../auth/UserRepository.kt` *(новий)* — інтерфейс: `findByEmail(email): UserRecord?`,
  `create(email, passwordHash): UserRecord`, `findById(id): UserRecord?`;
  `data class UserRecord(id, email, passwordHash)` — серверний тип, у `:core` не потрапляє.
- `server/.../auth/InMemoryUserRepository.kt` *(новий)* — мапа + `Mutex`, id через `UUID.randomUUID()`,
  email як ключ уже нормалізований.
- `server/.../auth/AuthService.kt` *(новий)* — `register(email, password)`, `login(email, password)`;
  уся валідація й нормалізація тут, роут лишається тонким. Помилки — sealed-результат `AuthResult`
  (`Success`, `ValidationFailed`, `EmailAlreadyTaken`, `InvalidCredentials`), роут мапить їх у статуси.
  Обидва виклики хешера загорнуті у `withContext(Dispatchers.Default)`: Ktor тримає обробники на пулі
  завбільшки з кількість ядер, і хешування просто в них дає анонімним запитам змогу морити голодом
  решту сервера. Хеш-заглушка рахується в конструкторі — це відбувається під час побудови графа Koin,
  до першого з'єднання.
- `server/.../plugins/Security.kt` *(новий)* — `install(Authentication) { jwt("auth-jwt") { ... } }`,
  `validate` дістає `userId` і повертає принципала; `challenge` віддає `401` + `ErrorResponseDto`
  (дефолтний челендж Ktor віддає порожнє тіло — клієнту нема що показати) і **сам ставить**
  `WWW-Authenticate`, бо разом із дефолтним челенджем губиться й цей заголовок.
- `server/.../routes/AuthRoutes.kt` *(новий)* — `authRoutes(authService)`;
  `meRoute(userRepository)` під `authenticate("auth-jwt")`.
- `server/.../di/ServerModule.kt` *(новий)* — Koin-модуль: `single<OpenLibraryClient>`, `single<AuthConfig>`,
  `single<PasswordHasher>`, `single<UserRepository>`, `single<JwtService>`, `single<AuthService>`.
- `server/.../Application.kt` *(правка)* — `install(Koin)`, `configureSecurity()`, реєстрація нових роутів,
  `searchRoutes` бере клієнта з Koin (`get()`); наявний `GET /` не чіпаємо.
- `server/.../plugins/Cors.kt` *(правка)* — `allowMethod(HttpMethod.Post)`, `allowHeader(Authorization)`,
  `allowHeader(ContentType)`. Без цього веб-клієнт із дев-сервера отримає CORS-помилку на кожен логін,
  і це виглядатиме як баг авторизації. `anyHost()` **замінено** на список origin-ів із
  `CORS_ALLOWED_ORIGINS`; незадана змінна = дев і пускає лише localhost, тож деплой, який її забув,
  падає закрито, а не відкривається всьому інтернету.
- `server/.../plugins/StatusPages.kt` *(новий, у плані не передбачався)* — `BadRequestException` → `400`,
  `ContentTransformationException` → `415`, обидва з `ErrorResponseDto`. Обробники **точкові**:
  catch-all на `Throwable` перевдягав би справжні 500-ті в клієнтські помилки. Виняток тут **не логується**:
  kotlinx-serialization цитує тіло запиту всередині повідомлення, а на цих роутах те тіло — чийсь пароль.
- `server/src/main/resources/logback.xml` *(правка)* — `root` зі скафолдного `trace` на `info`.

### Збірка
- `gradle/libs.versions.toml` — версії `koin`, `bcrypt`; бібліотеки `ktor-serverAuth`, `ktor-serverAuthJwt`
  (за наявним `ktor`-ref), `koin-ktor`, `koin-loggerSlf4j`, `bcrypt`.
- `server/build.gradle.kts` — ті самі залежності в `implementation`.
- Точні версії Koin/bcrypt фіксуємо на початку Implement після перевірки резолву — у плані їх не вгадуємо.

### Документація
- `README.md` — `JWT_SECRET` і `CORS_ALLOWED_ORIGINS` при `:server:run`.
- `AGENTS.md` — DI: на `:server` — Koin, у `:app:shared` поки що `AppContainer`; і виняток у неймінгу
  для серверних репозиторіїв.

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
- `AuthServiceTest` — рукописний `RecordingPasswordHasher` записує, **що саме** перевіряли:
  `verify` викликається рівно один раз і для відомої, і для невідомої адреси, і для невідомої —
  проти заглушки, а не проти чужого хеша. Час не асертимо (такий тест був би флакі на CI):
  асертимо наявність самого виклику, бо саме він і створює однаковий час. Той самий фейк ловить
  ім'я потоку й доводить, що хешування не лишається на потоці, який його викликав.
- `AuthConfigTest` — секрет і перекриття з env; відсутній і порожній секрет → зрозумілий виняток.
- `CorsTest` — дев-origin пускається, чужий сайт отримує відмову без `Access-Control-Allow-Origin`;
  розбір `CORS_ALLOWED_ORIGINS`.
- `PasswordLoggingTest` — примусово вмикає `TRACE` і асертить, що пароля немає в жодній події логу
  разом із усім ланцюгом `Caused by`. Рівень піднято навмисно: доводимо, що пароля не пише **ніхто**,
  а не що його ховає рівень логування.
- `ServerModuleTest` — `HttpClient` відпускається разом із графом Koin.
- Тести використовують `AuthConfig` з тестовим секретом і фейковий/дешевий хешер — `JWT_SECRET` з env не читають.
- Кожен тест на фікс перевірено «на вбивство»: прибираєш фікс — тест падає. Тест, який не падає
  на навмисній регресії, нічого не доводить.

Команди:
```
./gradlew :core:build
./gradlew :server:test
./gradlew build
JWT_SECRET=dev-secret ./gradlew :server:run
curl -i -X POST localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"password1"}'
curl -i -X POST localhost:8080/auth/login    -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"password1"}'
curl -i localhost:8080/me -H "Authorization: Bearer $TOKEN"
curl -i localhost:8080/me                       # 401 + WWW-Authenticate
curl -i -X POST localhost:8080/auth/register -d '{"email":"a@b.com","password":"password1"}'   # 415
curl -i "localhost:8080/search?q=dune&limit=3"  # не зламали наявний роут
```
Готово — тільки після зеленого прогону, не після написання коду.

## Commit

Гілка `feature/auth`. **База перевірена перед стартом:** `origin/main` уже містить змерджений
book-search (PR #1, `d694dbf`), тобто `Application.module` і `SearchRoutes` там є й рефакторити під
Koin є що. Локальний `main` при цьому був застарілий (стояв на скафолді `1031514`), тому гілку
взято саме від `origin/main`, а не від локального `main`. Атомарні коміти:
1. `build: add ktor auth, koin and bcrypt dependencies`
2. `feat(core): add auth wire contract and user domain model`
3. `refactor(server): wire dependencies through koin` *(разом з оновленими наявними тестами)*
4. `feat(server): add in-memory user storage and bcrypt hashing`
5. `feat(server): issue and verify jwt tokens`
6. `feat(server): add register, login and protected /me routes`
7. `docs: document JWT_SECRET and the server DI choice`

Далі — виправлення за підсумками рев'ю, кожне окремим комітом (див. наступний розділ).

`local.properties`, `build/`, будь-які реальні секрети — не комітимо. `JWT_SECRET` у git не потрапляє.

## Змінено після рев'ю

Знайдено рев'ю (спершу поодинці, потім п'ятьма паралельними агентами по вимірах). Кожен пункт —
окремий коміт із тестом, перевіреним на вбивство:

- **Пароль у логах.** Обробник `BadRequestException` логував сам виняток, а kotlinx-serialization
  цитує в ньому тіло запиту — на цих роутах це чийсь пароль. Підтверджено канаркою до фіксу.
  Обробники більше не логують виняток; `root` у logback — з `trace` на `info`.
- **BCrypt блокував потоки запитів.** Хешування пішло у `withContext(Dispatchers.Default)`.
  Вимір на 12 ядрах: під 96 паралельними логінами непов'язаний `GET /` мав 21.4 мс, після — 1.6 мс.
- **`login` не мав межі 72 байти**, яку мав `register`: довший пароль давав `500` із повідомленням
  BCrypt у тілі. Межа переїхала в одне місце, яке викликають обидва шляхи.
- **`anyHost()` у CORS** замінено на список origin-ів із `CORS_ALLOWED_ORIGINS`.
- **`WWW-Authenticate`** повернуто в `401` — власний `challenge` його губив.
- **`415` віддавав сирий рядок Ktor** з іменем внутрішнього класу — тепер `ErrorResponseDto`.
- **`HttpClient` не закривався** — Koin-визначення дістало `onClose`.

Хибні тривоги, які варто пам'ятати: паралельні агенти писали мутації в спільне робоче дерево й
забруднили одне одному вимір — двоє здали як «блокери» те, чого в коді немає. Мутаційні перевірки
мають жити в окремому worktree.

## Ризики й пастки

- **CORS.** Кожен новий метод чи заголовок треба заводити в `Cors.kt`, інакше браузер ріже запит ще
  до відправки, а виглядає це як баг авторизації. У проді ще й `CORS_ALLOWED_ORIGINS` має бути заданий.
- **72 байти BCrypt.** Довший пароль мовчки обрізається; ліміт має стояти на **обох** шляхах,
  бо BCrypt на довшому вводі кидає виняток, а не обрізає тихо.
- **Тіла запитів у логах.** Виняток розбору JSON цитує тіло; на auth-роутах його не можна логувати.
- **`Mutex` в in-memory сховищі.** «Перевірив, що email вільний → створив» без блокування дає
  двох користувачів з одним email під паралельними запитами.
- **Секрет у логах.** `AuthConfig` не логуємо цілком; якщо додаємо `toString`, секрет маскуємо.
- **Дані живуть лише до рестарту** — це очікувано для in-memory, але сказано в README,
  щоб потім не шукали баг «зникли користувачі». Наслідок для клієнта: валідний токен після рестарту
  дає `401`, бо запису вже немає.

## Відоме й свідомо не закрите

Усі «варто» зі зведеного рев'ю закриті окремими комітами: коди помилок і `field` у контракті,
тіла для `404`/`405`/`406`, межі довжини email і розміру тіла, rate limiting на обох auth-роутах,
індекс за id замість скану, і три тестові прогалини (`issuer`, `audience`, токен із `login`).
Лишається таке:

- **Реєстрація перелічує адреси за конструкцією:** `409` прямо каже, що email зайнятий. Rate limiting
  робить перебір дорогим, але не неможливим. Закрити повністю = не підтверджувати адресу взагалі,
  а це вже зміна флоу реєстрації й продуктове рішення. Тому вирівняний тайминг логіну сам по собі
  повним захистом від перебору не є.
- **Ліміт тіла перевіряє лише оголошений `Content-Length`.** Chunked-запит приходить без нього
  й проходить повз; для цього потрібен ліміт на саме читання — доречно, коли бекенд почне приймати файли.
- **Ключ rate limiting — адреса клієнта.** За спільним NAT це грубо; вибір свідомий, бо альтернатива
  (заголовок від проксі) підробляється тим, від кого ми й захищаємось.
- **Гілка «валідний підпис, користувача немає»** досі без тесту: щоб її перевірити, потрібен спосіб
  прибрати користувача, а API видалення немає. З'явиться разом із PostgreSQL.
- **Дублювання фікстур у тестах** (`cost 4`, `test-secret`, `UserRecord`) — нітпік, не чіпав.
