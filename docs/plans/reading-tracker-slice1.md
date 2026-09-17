# План: трекер читання, Зріз 1 (запис сесій + базова статистика)

> Живий план для агентів. Джерело істини по поведінці — `docs/specs/reading-tracker.md`.
> Читати разом з `AGENTS.md` і скілами `write-tests`, `book-fixtures`. Робота — строго за 5-фазним процесом.
> Статус: **план погоджено. Implement — з Кроку 0 (спайк драйверів SQLDelight).**

## Скоуп Зрізу 1

**У скоупі:** запис сесії читання (`від`→`до`, `від` за замовчуванням = закладка, валідація);
день читання з межею 04:00; статистика «сьогодні / за тиждень (рухоме 7-денне вікно) / всього / прогрес %»;
список сесій; редагування й видалення; локальне збереження.

**Повністю покриває AC:** 1–6, 9, 11, 12, 40, 47, 48, 50, 77–79, 81.
**Частково покриває:** AC-13 і AC-49 — in-scope половину закриваємо тут, решта в Зрізі 2 (див. «Рішення 6»).
Жоден частково покритий AC не вважається закритим.

**Поза Зрізом 1, не проєктуємо зараз:** денна ціль, активний день, streak, заморозки, дедлайн, темп,
прогноз дочитання, дочитані книги, рекомендації. Структура має прийняти їх без переробки — як саме,
описано в розділі «Запас на наступні зрізи».

## Рішення, які пропоную (потребують погодження)

### 1. `kotlinx-datetime` як нова залежність `:core`

День читання, рухоме вікно й «сьогодні» — це арифметика календарних дат, а не міліcекунд.
Без бібліотеки довелося б рахувати дати вручну, і `readingDayOf` перестала б бути тривіально перевірною.
`kotlinx-datetime` — чистий multiplatform, без Android/Ktor/Compose, тобто духу правила `:core` не порушує.
Але **буква правила в `AGENTS.md`** («дозволені залежності: лише kotlinx-serialization і coroutines`)
її забороняє — тож частиною цього зрізу є правка `AGENTS.md`. Окремим кроком, свідомо.

### 2. Час не береться з системи всередині домену

Усі чисті функції отримують `today: LocalDate` / `now: Instant` і `TimeZone` **параметрами**.
`Clock.System` живе тільки у presentation-шарі. Інакше AC-3 (майбутній день), AC-9 (час 22:10),
AC-11/12 (01:30 і 04:30) і AC-40 (межі вікна) неможливо перевірити без підміни системного годинника.

### 3. Сховище — SQLDelight у `:app:shared`, але з гейтом на вебі

Це **перша БД у проєкті**. SQLDelight дає типобезпечні запити й `Flow` з коробки, що прямо закриває AC-50.
Ризик — драйвери під усі таргети `:app:shared` (android, iosArm64, iosSimulatorArm64, js, wasmJs).
Android/iOS закриті штатними драйверами; **web (js + wasmJs) не перевірений**, а `:app:webApp` збирається
під обидва. Тому Крок 0 — спайк із явним рішенням, а не припущення (див. «Ризики»).

### 4. Мінімальна локальна сутність книги — `TrackedBook`

AC-47 («обсяг невідомий → прогрес прихований») і AC-48 («118 з 300 → 39 %») не перевірити, поки нема
де тримати `totalPages`. У домені `Book` цього поля нема, і Open Library його не дає стабільно.
Тому в Зрізі 1 з'являється локальна `TrackedBook(bookKey, title, authors, coverUrl, totalPages: Int?)`,
де `totalPages` вводить користувач і може не вводити взагалі.

Це **розширення скоупу відносно твого переліку** — без нього два AC зі скоупу неперевірні.
Тримаю його мінімальним: жодних полиць, статусів, синхронізації — рівно те, до чого чіпляється сесія.

### 5. Пакет `ua.readshelf.domain.reading`

Наявний домен плаский (`Book.kt`, `BookRepository.kt`). Трекер додає ~8 файлів, і в пласкому вигляді
вони перемішаються з пошуком. Підпакет лишається в межах правила неймінгу `ua.readshelf.<layer>`.

### 6. Два AC покриваються частково — і позначені частковими

**AC-13** («це один день читання; streak зростає на 1, не на 2») і **AC-49** (перелічує ціль, streak
і запас заморозок) містять половину, для якої в Зрізі 1 нема механізму.

Правило: in-scope половину перевіряємо тут, а сам AC у Verify-таблиці має статус
**«частково → Зріз 2»**, а не «закрито». Частково покритий AC не зараховується як виконаний ні в
цьому плані, ні в описі коміта.

- **AC-13** — тут: обидві сесії (23:00 і 02:00) дають **один** день читання. У Зріз 2: streak +1, не +2.
- **AC-49** — тут: «сьогодні / за тиждень / всього» однакові на екрані будь-якої з трьох книг і
  враховують усі три. У Зріз 2: те саме для цілі, streak і запасу заморозок.

Це дорожче за «записати закритим», але дешевше, ніж шукати потім, які AC насправді не перевірені.

## Кроки

### Крок 0 — спайк: драйвери SQLDelight під усі таргети (гейт)

Перед будь-яким кодом фічі: завести SQLDelight у `:app:shared` з порожньою схемою й домогтися
зеленого `./gradlew build` (включно з js, wasmJs, iosArm64). Мета — не функціонал, а відповідь на питання,
чи існує робочий web-драйвер для нашої версії.

Розвилка, яку треба зафіксувати до Кроку 4:
- **web працює** → один `ReadingSessionRepositoryImpl` на SQLDelight для всіх таргетів;
- **web не працює** → `expect`/`actual` фабрика сховища: SQLDelight на android/ios, окрема реалізація
  для web. Домен і ViewModel при цьому не змінюються — вони бачать лише інтерфейс репозиторію з `:core`.

Крок 0 нічого не комітить у домен і може бути відкинутий цілком.

### Крок 1 — залежності

`gradle/libs.versions.toml`: `kotlinx-datetime`; плагін SQLDelight + `runtime`, `android-driver`,
`native-driver`, web-драйвер за підсумком Кроку 0, `sqlite-driver` (JVM, лише для тестів `androidHostTest`).
Підключити: `kotlinx-datetime` у `:core` і `:app:shared`; SQLDelight — тільки в `:app:shared`.

**Звірити ім'я тестової задачі `:core` — не припускати.** У М1 нас уже вкусило `:app:shared:jvmTest`,
якого не існує (в `:app:shared` немає jvm-таргету). У `core/build.gradle.kts` `jvm()` **є**, і `:server`
від `:core` залежить, тож задача майже напевно на місці — але перевіряємо фактом, а не міркуванням:

```
./gradlew :core:tasks --all | grep -i jvmTest
```

Якщо задачі нема — з'ясувати фактичне ім'я (`./gradlew :core:tasks --all | grep -i test`) і
виправити Крок 3, розділ Verify і `AGENTS.md` до того, як написано перший тест.

> **Звірено на Кроці 0:** `:core:jvmTest` існує (`jvmTest - Runs the tests of the test test run`).

**Залежності host-тестів `:app:shared` — тільки через `getByName("androidHostTest")`.**
Типізованого акцесора `androidHostTest` у DSL плагіна `com.android.kotlin.multiplatform.library` немає:
`androidHostTest.dependencies { }` падає ще на конфігурації з `Unresolved reference 'androidHostTest'`
(спіймано на Кроці 0). Робочий запис:

```
getByName("androidHostTest").dependencies {
    implementation(libs.sqldelight.sqliteDriver)
}
```

### Крок 2 — `:core`, домен (`ua.readshelf.domain.reading`)

Сигнатури, не реалізація:

- `ReadingSession(id: String, bookKey: String, fromPage: Int, toPage: Int, day: LocalDate, recordedAt: Instant?)`
  - `val pages: Int` = `toPage - fromPage` (§5.1 спеки; `recordedAt = null` — «час невідомий»).
- `TrackedBook(bookKey: String, title: String, authors: List<String>, coverUrl: String?, totalPages: Int?)`
- `ReadingDay` — `const val CUTOFF_HOUR = 4`; `fun of(instant: Instant, zone: TimeZone): LocalDate`.
- `SessionDraft(bookKey, fromPage, toPage, day, recordedAt)` — те, що прийшло з форми.
- `SessionRejection` — `NotForward`, `NegativeFrom`, `DayInFuture`, `BeyondTotalPages`.
- `ValidateSessionUseCase(draft, today: LocalDate, totalPages: Int?): SessionRejection?`
- `ReadingStats.kt` — чисті top-level функції над `List<ReadingSession>`:
  `pagesOn(sessions, day)`, `pagesInWeek(sessions, today)`, `pagesTotal(sessions)`,
  `bookmarkOf(sessions, bookKey)`, `progressPercent(sessions, bookKey, totalPages): Int?`
- `BuildReadingSummaryUseCase(sessions, books, today): ReadingSummary` — композиція для UI.
- `ReadingSessionRepository` — `observeAll(): Flow<List<ReadingSession>>`, `add`, `update`, `delete(id)`.
- `TrackedBookRepository` — `observeAll(): Flow<List<TrackedBook>>`, `upsert`, `delete(bookKey)`.

Домовленості, що випливають зі спеки і які легко зіпсувати:
- `bookmarkOf` = **максимум** `toPage` по книзі, не `toPage` останньої за часом. §5.1: сесія з
  `до < закладки` — це перечитування, вона закладку не опускає.
- `pagesInWeek` — вікно `[today − 6, today]`, сім днів включно (§5.5, AC-40).
- `progressPercent` повертає `null`, якщо `totalPages` невідомий або ≤ 0 (AC-47).
- Жодних збережених лічильників: усе виводиться зі списку сесій на льоту.

### Крок 3 — `:core`, тести чистих функцій (без БД)

`core/src/commonTest/kotlin/ua/readshelf/domain/reading/`:
`ReadingDayTest`, `ValidateSessionUseCaseTest`, `ReadingStatsTest`, `BuildReadingSummaryUseCaseTest`.
Запуск — `./gradlew :core:jvmTest`. Імена — camelCase (backticks заборонені в `commonTest`, скіл write-tests §3).

### Крок 4 — `:app:shared`, сховище (`ua.readshelf.data.local`)

- `TrackedBookEntity.sq` і `ReadingSessionEntity.sq` — таблиці `trackedBookEntity` і `readingSessionEntity`.
  Суфікс `Entity` — бо SQLDelight генерує класи за назвами таблиць і `ReadingSession` зіткнувся б із доменним.
- **Без `REFERENCES … ON DELETE CASCADE` (відхід від першої редакції плану).** SQLite перевіряє зовнішні
  ключі лише коли кожен драйвер вмикає їх окремо на з'єднанні (Android, native, JDBC, воркер — чотири місця,
  де забуте налаштування мовчки ламає каскад). До того ж `INSERT OR REPLACE` на книзі з каскадом стер би її
  сесії. Тому §7 спеки («видалення книги забирає її сесії») виконується явним видаленням сесій у тій самій
  транзакції в `TrackedBookRepositoryImpl.delete`.
- **Upsert книги — `INSERT OR REPLACE`**, не `ON CONFLICT DO UPDATE`: останнє потребує SQLite 3.24,
  а Android API 24 має 3.9.
- Фабрика драйвера — `fun interface SqlDriverFactory` у `commonMain` з реалізаціями
  `AndroidSqlDriverFactory(context)`, `NativeSqlDriverFactory`, `WebWorkerSqlDriverFactory`
  (замість `expect fun`: Android-реалізації потрібен `Context`, який у спільному коді не взяти).
- **Web — власний воркер на `@sqlite.org/sqlite-wasm` з OPFS (VFS `opfs-sahpool`)**, а не офіційний sql.js:
  той тримає БД у пам'яті, і після перезавантаження сторінки дані зникали б (AC-78/79).
  Воркер реалізує протокол web-worker-driver 2.1.0 (формат звірено з вихідним кодом драйвера, не з документацією,
  яка з ним розходиться): відповідь `{ id, results: { values } }`, для змінювальних запитів —
  `values: [[кількість змінених рядків]]`; цілі числа — лише JS `number`, ніколи `BigInt`.
  Обмеження `opfs-sahpool`: ексклюзивний замок — друга вкладка застосунку не відкриє БД, поки відкрита перша.
- **Версія схеми на вебі — `PRAGMA user_version`** (`SqlDriver.createOrMigrate`). Android і native драйвери
  роблять це самі; для персистентного воркера без цього схема створювалася б при кожному старті.
- **`generateAsync = true` — рішення, а не налаштування.** `web-worker-driver` існує лише в
  асинхронному вигляді, тож без цього прапорця web-таргети не отримають робочої БД. Прапорець діє на
  генерацію для **всіх** таргетів, тому **згенерований API — suspend скрізь**, включно з Android та iOS:
  - запити виконуються через `awaitAsList()` / `awaitAsOne()` / `awaitAsOneOrNull()`, а не `executeAsList()`;
  - створення схеми — `ReadShelfDatabase.Schema.awaitCreate(driver)`, не синхронний `create`;
  - `asFlow().mapToList(dispatcher)` лишається робочим і для асинхронних запитів;
  - на наш дизайн це майже не впливає — методи репозиторіїв і так `suspend`, — але синхронний
    виклик БД тепер неможливий навіть там, де драйвер синхронний. Не обходити через `runBlocking`.
- `ReadingSessionRepositoryImpl`, `TrackedBookRepositoryImpl` — мапінг рядок ↔ домен, запити на
  `Dispatchers.Default` (не на UI), читання через `asFlow().mapToList()`.
- Дата в БД — `TEXT` у ISO-форматі (`LocalDate.toString()`), час — епоха в мілісекундах, nullable.
  Не `INTEGER`-дата: день читання має лишатись читабельним при ручному розборі БД.

### Крок 5 — тести репозиторію

`app/shared/src/androidHostTest/` (не `commonTest` — там нема драйвера під web/ios):
`ReadingSessionRepositoryImplTest` на `JdbcSqliteDriver`. Покрити: запис і читання назад; `update`;
`delete`; каскад при видаленні книги; **перевідкриття БД із файлу** — запис лишився (AC-78/79).

### Крок 6 — presentation

- `ReadingUiState` — список сесій, зведення, стан форми, `rejection: SessionRejection?`.
- `ReadingViewModel(sessionRepo, bookRepo, validate, buildSummary, clock, zone)` —
  `onToPageChange`, `onSave`, `onEdit`, `onDelete`; `від` підставляється з `bookmarkOf`.
- `Clock` і `TimeZone` — параметри конструктора з дефолтами `Clock.System` / `TimeZone.currentSystemDefault()`,
  щоб тест підставляв фіксовані (скіл write-tests §2: тестованість закладаємо в продакшн-код).
- **Local-first:** `onSave` спершу дочікується `repository.add(...)`, і лише потім чистить форму.
  Жодного оптимістичного оновлення: інакше AC-78 не виконується (§6 спеки).
- Тести — `ReadingViewModelTest` у `commonTest` із рукописним `FakeReadingSessionRepository`
  (мок-бібліотек у проєкті нема), `StandardTestDispatcher` + `Dispatchers.setMain`.

### Крок 7 — UI

`ua.readshelf.ui.reading`:
- `ReadingScreen` — зведення + список сесій;
- `SessionForm` — поле `до`, `від` показано й редаговане, помилка валідації;
- **`BookDetailsForm` — редагування `totalPages` книги** (порожнє поле = обсяг невідомий);
- дії редагування й видалення сесії.

`totalPages` потрібне окремою точкою вводу, бо форма сесії його не містить: без неї AC-48 (39 %)
перевірявся б лише на сід-даних у тесті, а живого шляху задати обсяг у застосунку не було б.
Порожнє значення — легальний стан, а не помилка: на ньому тримається AC-47.

Жодних викликів репозиторію з composable. **Жодного індикатора завантаження мережі** (AC-81).

### Крок 8 — `AppContainer`

Конструкторне впровадження, як у `searchViewModel()` — щоб міграція на Koin у М2 лишалася механічною.
Як зроблено (відхилення від «lazy-синглтонів» першої редакції):

- **Відкриття БД — `suspend`** (web-драйвер відкриває її у воркері), тож lazy-синглтон неможливий.
  `AppContainer.openReadingStorage(factory)` відкриває сховище один раз за процес під `Mutex`
  і повертає `ReadingStorage` — лише доменні інтерфейси репозиторіїв; невдача не кешується.
- **Фабрику драйвера передає точка входу:** `App(sqlDriverFactory)`. Android — `AndroidSqlDriverFactory(applicationContext)`,
  iOS — `NativeSqlDriverFactory()`, web — `WebWorkerSqlDriverFactory()`. Так Android-`Context` не живе в глобальному стані.
- **Навігація — дві вкладки (Search / Reading)** зі спільним `ReadingViewModel`, створеним на рівні `App`:
  кнопка «Track» у пошуку і трекер ділять один стан. Поки сховище не відкрите, «Track» прихована.
- **Невдале відкриття сховища** показується текстом у вкладці Reading, а не валить застосунок:
  на вебі OPFS-пул тримає ексклюзивний замок, тож друга вкладка відкрити БД не зможе.
- **Воркер `readshelf-sqlite.worker.js` і npm-залежність `@sqlite.org/sqlite-wasm` — у `:app:webApp`.**
  Ресурси бібліотеки (`:app:shared`) не копіюються в бандл застосунку — webpack не знаходив воркер.

### Крок 9 — `AGENTS.md`

Дописати: `kotlinx-datetime` дозволена в `:core`; `:app:shared` має локальну БД на SQLDelight;
тести репозиторію живуть в `androidHostTest`, бо драйвера під web/ios у `commonTest` нема.

> Виконано по ходу Кроків 0–8 (кожна правка — у коміті, що її спричинив), а не окремим кроком:
> коміт, який вводить правило, не мав суперечити `AGENTS.md`.

## Фікстури

За скілом `book-fixtures` — реальні книги, взяті через `mcp__openlibrary__search_books`.
Зафіксувати в тестах як константи, мережу в тестах не смикати:

| bookKey | title | author | coverUrl |
|---|---|---|---|
| `/works/OL893414W` | Dune | Frank Herbert | `https://covers.openlibrary.org/b/id/11481354-M.jpg` |
| `/works/OL27482W` | The Hobbit | J.R.R. Tolkien | `https://covers.openlibrary.org/b/id/14627509-M.jpg` |
| `/works/OL21745884W` | Project Hail Mary | Andy Weir | `https://covers.openlibrary.org/b/id/11200092-M.jpg` |

`totalPages` — не з Open Library (нестабільне), задається у фікстурі: Dune = 300 для AC-48,
The Hobbit = `null` для AC-47.

## Verify — AC зі скоупу

Статуси: **повністю** — AC закритий Зрізом 1; **частково** — перевірена лише in-scope половина,
AC лишається відкритим до названого зрізу.

| AC | Суть | Чим перевіряємо | Статус |
|---|---|---|---|
| AC-1 | 92→118 = 26 сторінок, закладка 118 | `ReadingStatsTest`: `pages`, `bookmarkOf` | повністю |
| AC-2 | `до ≤ від` відхиляється | `ValidateSessionUseCaseTest`: `NotForward` для 118→118 і 118→90 | повністю |
| AC-3 | день у майбутньому відхиляється | `ValidateSessionUseCaseTest`: `DayInFuture` при `day = today + 1` | повністю |
| AC-4 | 92→118 + 118→140 = 48, закладка 140 | `ReadingStatsTest`: `pagesOn`, `bookmarkOf` | повністю |
| AC-5 | правка 118→130 тягне всю статистику | `ReadingStatsTest` на зміненому списку + `ReadingSessionRepositoryImplTest.update` | повністю |
| AC-6 | видалення повертає стан | `ReadingStatsTest`: статистика списку без сесії == статистика до її додавання | повністю |
| AC-9 | час 22:10 записується сам | `ReadingViewModelTest` із фіксованим `Clock` → `recordedAt` = 22:10 | повністю |
| AC-11 | 01:30 → попередня календарна доба | `ReadingDayTest` | повністю |
| AC-12 | 04:30 → поточна доба | `ReadingDayTest` | повністю |
| AC-13 | 23:00 і 02:00 — один день читання | `ReadingDayTest` — тотожність дня читання | **частково → Зріз 2** (streak +1, не +2) |
| AC-40 | −6 днів у вікні, −7 поза ним | `ReadingStatsTest.pagesInWeek` на межах | повністю |
| AC-47 | `totalPages = null` → прогрес прихований | `ReadingStatsTest.progressPercent` == `null`; порожнє поле у `BookDetailsForm` | повністю |
| AC-48 | 118 з 300 → 39 % | `ReadingStatsTest.progressPercent` == 39; `totalPages` задається через `BookDetailsForm` | повністю |
| AC-49 | наскрізність по трьох книгах | `BuildReadingSummaryUseCaseTest` на трьох фікстурах — «сьогодні / за тиждень / всього» | **частково → Зріз 2** (ціль, streak, запас заморозок) |
| AC-50 | статистика оновлюється одразу | `ReadingViewModelTest`: після `onSave` стан містить нове зведення без ручного перезавантаження | повністю |
| AC-77 | усе працює офлайн | Структурно: у графі трекера нема `HttpClient`. Перевірка — `ReadingViewModelTest` із fake-репозиторіями + ручний смоук у режимі польоту | повністю |
| AC-78 | запис переживає закриття застосунку | `ReadingSessionRepositoryImplTest`: запис → закрити драйвер → відкрити файл заново → запис на місці | повністю (з поправкою з «Ризики» §4) |
| AC-79 | статистика після перезапуску та сама | Той самий тест: зведення, пораховане з перечитаних із файлу сесій, збігається | повністю |
| AC-81 | нема стану очікування мережі | У `ReadingUiState` немає статусу завантаження мережі; перевірка ревʼю + `ReadingViewModelTest` | повністю |

### Достроково покрите — не зараховувати

`bookmarkOf` = максимум `toPage` (а не `до` останньої за часом сесії) означає, що сесія з
`до < закладки` закладку не опускає — тобто **частина AC-8** (перечитування) виконується вже в
Зрізі 1, хоч сам AC поза скоупом. Це приємний доказ, що інваріант обрано правильно, і не більше:
решта AC-8 (сторінки перечитування йдуть у «сьогодні» й у денну ціль) вимагає механізмів Зрізу 2.
У Verify Зрізу 1 AC-8 **не зараховуємо** — за тим самим правилом, що й AC-13 з AC-49.

**Команди:**
```
./gradlew :core:jvmTest                     # ім'я звіряємо на Кроці 1, не припускаємо
./gradlew :app:shared:testAndroidHostTest
./gradlew build
```
Тест зелений — не написаний, а прогнаний (скіл write-tests).

## Запас на наступні зрізи

Що саме дає змогу додати відкладене без переробки:

- **Ціль, активний день, streak, заморозки** — усе це функції від `List<ReadingSession>` + денної цілі.
  Оскільки в Зрізі 1 нема жодного збереженого лічильника, вони додаються як нові чисті функції поруч
  із `pagesOn`/`pagesInWeek`, не чіпаючи ні сховище, ні наявні.
- **Історія цілей** (§5.2: «зміна цілі не переписує минуле») потребуватиме своєї таблиці. Схема Зрізу 1
  її не блокує — нова таблиця, без міграції наявних.
- **Темп, прогноз, дедлайн** — те саме: похідні від сесій + `totalPages`, який уже є в `TrackedBook`.
- **Дочитані книги** — прапорець у `trackedBook` + нові запити; сесії не чіпаються.
- **Рекомендації** — чисте правило над уже наявним зведенням.

Єдине, що варто закласти зараз і що потім дорого: **міграції SQLDelight**. З першої версії схеми
тримати `.sq` під версіонуванням і не правити її «на місці» після першого релізу.

## Ризики

1. **Web-драйвер SQLDelight (js + wasmJs).** Найбільший ризик зрізу; я його не перевіряв.
   Знімається Кроком 0 до того, як написано хоч рядок домену. Запасний шлях — `expect`/`actual`
   сховище — коштує один додатковий файл на таргет і не зачіпає ні домен, ні ViewModel.
2. **`kotlinx-datetime` у `:core`** — розширює дозволений список залежностей. Якщо це небажано,
   альтернатива — тримати `ReadingDay` у `:app:shared`, але тоді статистика перестає бути цілком
   у `:core` і ламається інваріант, який ти задав.
3. **`TrackedBook` як розширення скоупу** — див. «Рішення 4». Без нього AC-47 і AC-48 треба виносити зі зрізу.
4. **AC-78/79 в юніт-тесті** перевіряються через перевідкриття файлу БД, а не через реальне вбивство
   процесу. Справжній перезапуск лишається ручним смоуком.
