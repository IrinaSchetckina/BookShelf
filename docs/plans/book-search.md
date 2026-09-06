# План: фіча пошуку книг (feature/book-search)

> Живий план для агентів. Читати разом з `AGENTS.md`. Робота — строго за 5-фазним процесом.
> Статус: Explore + Plan завершено й погоджено. Наступне — Implement.

## Рішення (погоджені)
- **Пакети:** уся кодова база уніфікована на `ua.readshelf.*` (`domain`, `contract`, `data.remote`, `presentation`, `ui`). Двох коренів немає.
- **DI:** без Koin. Простий `AppContainer` у `:app:shared` — конструкторне впровадження. Важкі залежності (`HttpClient`, `ReadShelfApi`, `BookRepositoryImpl`) — singletons (lazy); `searchViewModel()` — фабрика (новий екземпляр щоразу). Заміна на Koin у М2 буде механічною.
- **ViewModel в UI:** через `viewModel { AppContainer.searchViewModel() }` (не `remember`). `SearchViewModel : androidx.lifecycle.ViewModel`, корутини у `viewModelScope`.
- **Контракт:** `BookDto`/`SearchResponseDto` — у `:core`, пакет `ua.readshelf.contract`, `@Serializable` лише на DTO. Домен `Book`/`BookRepository` — `ua.readshelf.domain`, чистий. У `:core` лише kotlinx-serialization + coroutines; жодних Android/Ktor/Compose.
- **Мережа клієнта:** тільки через наш `:server`, не напряму в Open Library.

## Open Library
`GET https://openlibrary.org/search.json?q=<q>&limit=20&fields=key,title,author_name,first_publish_year,cover_i`
Без ключа. Поля нестабільні → `ignoreUnknownKeys = true` обов'язково. Обкладинка: `cover_i` → `https://covers.openlibrary.org/b/id/<cover_i>-M.jpg`.

## Кроки

### Крок 0 — інфраструктура збірки
`libs.versions.toml`: плагін kotlinSerialization; kotlinx-serialization-json, kotlinx-coroutines-core, ktor-client-core, ktor-client-okhttp, ktor-client-darwin, ktor-client-js, ktor-client-content-negotiation, ktor-serialization-kotlinx-json, ktor-server-content-negotiation, ktor-server-cors, ktor-client-mock (тести).
Підключити плагіни/залежності у `:core`, `:server`, `:app:shared` (клієнтський рушій per-target: okhttp/darwin/js).

### Крок 1 — `:core` (домен + контракт)
- `ua.readshelf.domain.Book` — `data class Book(id, title, authors: List<String>, firstPublishYear: Int?, coverUrl: String?)`.
- `ua.readshelf.domain.BookRepository` — `suspend fun search(query, limit): List<Book>`.
- `ua.readshelf.domain.SearchBooksUseCase` — валідація порожнього запиту + виклик репозиторію.
- `ua.readshelf.contract.SearchDto` — `@Serializable BookDto`, `SearchResponseDto` (контракт нашого API).
- Мапінг `BookDto.toDomain()`.

### Крок 2 — `:server` (`GET /search`)
- `data.remote.OpenLibraryDto` — `@Serializable` DTO відповіді Open Library, `ignoreUnknownKeys`.
- `data.remote.OpenLibraryClient` — `HttpClient(CIO)` + ContentNegotiation; конструктор приймає `HttpClient` (тест через MockEngine).
- `data.OpenLibraryMapper` — Open Library DTO → `BookDto`.
- `plugins/Serialization.kt`, `plugins/Cors.kt` — `install(ContentNegotiation)`, `install(CORS)`.
- `routes/SearchRoutes.kt` — `GET /search?q=&limit=`: порожній `q` → 400; помилка апстріму → 502; успіх → `SearchResponseDto`.
- `Application.kt` — підключити плагіни й `searchRoutes()`; наявний `GET /` не чіпати.

### Крок 3 — `:app:shared` (мережа + presentation + UI)
- `data.remote.ReadShelfApi` — Ktor Client до нашого сервера.
- `data.remote.ApiConfig` — `expect val apiBaseUrl`; actual: androidMain `http://10.0.2.2:8080`, iosMain `http://localhost:8080`, webMain `http://localhost:8080`. (`:app:shared` не має jvm-таргету, тож jvmMain-actual не потрібен.)
- `data.BookRepositoryImpl` — реалізація `BookRepository`.
- `presentation.SearchViewModel : ViewModel` — `StateFlow<SearchUiState>` (Idle/Loading/Success/Empty/Error), `onQueryChange`, `onSearch`; корутини у `viewModelScope`.
- `ui.SearchScreen` — TextField + кнопка + LazyColumn(BookRow) + стани; VM через `viewModel { AppContainer.searchViewModel() }`; нуль мережі в composable.
- `AppContainer` — singletons (client/api/repo) + `searchViewModel()` фабрика.
- `App.kt` — замінити демо на `SearchScreen`.

### Крок 4 — платформні точки входу
- `AndroidManifest.xml` — `<uses-permission android:name="android.permission.INTERNET"/>` + `usesCleartextTraffic="true"` для дев-збірки.
- iOS/web/MainActivity — тонкі, без змін (усе в `App()`).

## Verify
Тести:
- `:server` — `SearchRoutesTest` (testApplication + MockEngine: 200/валідний JSON; q="" → 400; апстрім 500 → 502); `OpenLibraryMapperTest` (з відсутніми `author_name`/`cover_i`).
- `:app:shared` — `BookRepositoryImplTest` (MockEngine, парсинг+мапінг); `SearchViewModelTest` (Loading → Success/Error).

Команди:
```
./gradlew :core:build
./gradlew :server:test
./gradlew :app:shared:testAndroidHostTest
./gradlew build
./gradlew :server:run
curl "http://localhost:8080/search?q=dune&limit=5"
curl -i "http://localhost:8080/search?q="        # 400
./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
```
Порядок доведення до зеленого: спершу `:server` + web, потім Android, iOS — перевірка компіляції через `./gradlew build`.

## Commit
Гілка `feature/book-search`, атомарні коміти:
1. build-інфраструктура + `:core` домен/контракт
2. `:server` `/search` + тести
3. клієнтський шар + екран
4. Android-маніфест

`local.properties`/`build/` не чіпати.

## Поза скоупом
Авторизація, полиці, PostgreSQL/Exposed, ktlint, кешування, пагінація, дебаунс вводу.
