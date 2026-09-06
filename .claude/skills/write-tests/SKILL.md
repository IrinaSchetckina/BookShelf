---
name: write-tests
description: Як пишуться тести в ReadShelf — інструменти, що покривати, конвенції. Читати перед написанням або правкою будь-якого тесту в репозиторії (Ktor-роути в :server; мапери DTO, репозиторії, ViewModel у :app:shared).
---

# Як ми пишемо тести в ReadShelf

## Інструменти (інших не вводимо)

| Що тестуємо | Інструмент | Де живе |
|---|---|---|
| Ktor-роут | `testApplication { application { module(fake) } }` | `server/src/test/kotlin/ua/readshelf/` |
| Будь-який HTTP-виклик (наш або зовнішній) | Ktor `MockEngine` | обидва модулі |
| Чиста функція мапінгу | звичайний unit-тест, без HTTP | поруч із роутами |
| ViewModel / suspend-логіка | `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) | `app/shared/src/commonTest/kotlin/ua/readshelf/` |
| Доменні залежності у ViewModel | рукописний `Fake*`, **не** мок-бібліотека | там само |

Асерти — тільки `kotlin.test` (`assertEquals`, `assertIs`, `assertTrue`, `assertFailsWith`).
Мок-бібліотек (MockK, Mockito) у проєкті немає й не додаємо.

## Жорсткі правила

1. **Ніякої реальної мережі в тестах.** Open Library і наш `:server` завжди за `MockEngine`.
2. **Тестованість закладаємо в продакшн-код:** `HttpClient` і `baseUrl` — параметри конструктора з дефолтами (`OpenLibraryClient`, `ReadShelfApi`); `Application.module()` приймає `OpenLibraryClient`. Якщо клас неможливо протестувати без підміни — міняємо конструктор, а не тест.
3. **Імена тестів:** у `:server` (JVM) — backticks з описом поведінки: ``fun `rejects a blank query with 400`()``. У `commonTest` backticks заборонені (js/wasm/native) — camelCase: `emitsLoadingThenSuccess()`. Ім'я описує поведінку, не метод.
4. **Структура тесту:** підготовка → одна дія → асерти. Порожній рядок між блоками.
5. **Фікстури** — приватні top-level: `private const val OPEN_LIBRARY_PAYLOAD`, `private fun repositoryWith(engine)`, `private fun book(id)`. JSON-payload — сирий рядок, як його реально віддає сервіс, не зібраний з DTO.
6. **Асерт на URL запиту** там, де важливі параметри: захоплюємо `request.url` у `MockEngine` і перевіряємо `contains("q=dune")` з повідомленням `"actual url: $requestedUrl"`.
7. Спільні тести ганяємо через `./gradlew :app:shared:testAndroidHostTest` — jvm-таргету в `:app:shared` немає.

## Що зобов'язані покрити

Для **роута** (`SearchRoutesTest`):
- happy-path: статус 200 + розпарсене тіло, поле за полем;
- валідація входу → **400**: усі форми порожнечі (`?q=`, `?q=%20%20`, параметра немає взагалі);
- падіння апстріму → **502** (`respondError(InternalServerError)`);
- нормалізація параметрів: clamp `limit=999` → `limit=50`, перевірка через захоплений URL;
- у тесті на 400 движок має **вибухати** при виклику: `MockEngine { error("Open Library must not be called for a blank query") }` — так ми доводимо, що апстрім не смикнули.

Для **мапера** (`OpenLibraryMapperTest`): відсутні й порожні поля. Дані з Open Library нестабільні, тож обов'язково — документ без `title` (і з `"   "`), без `author_name`, без `cover_i`. Перевіряємо і що записи без назви **відкидаються**, і що `coverUrl` будується лише за наявності `cover_i`.

Для **репозиторію** (`BookRepositoryImplTest`): парсинг реального payload'у нашого API + мапінг у домен; невідоме поле в JSON не ламає парсинг; помилка бекенду прокидається як виняток (`assertFailsWith`).

Для **ViewModel** (`SearchViewModelTest`): усі стани `SearchUiState.Status` — `Loading → Success`, `Empty`, `Error` (з текстом), `Idle` для порожнього запиту + перевірка, що репозиторій не викликали.

## Приклади з нашого коду

### Роут через `testApplication` + `MockEngine`

```kotlin
private fun mockOpenLibraryClient(engine: MockEngine): OpenLibraryClient =
    OpenLibraryClient(
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
    )

@Test
fun `maps an upstream failure to 502`() = testApplication {
    val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
    application { module(mockOpenLibraryClient(engine)) }

    assertEquals(HttpStatusCode.BadGateway, client.get("/search?q=dune").status)
}
```

### Захоплення URL і відповідь JSON'ом

```kotlin
val engine = MockEngine { request ->
    requestedUrl = request.url.toString()
    respond(
        content = OPEN_LIBRARY_PAYLOAD,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
```
Без заголовка `Content-Type: application/json` ContentNegotiation не розпарсить тіло — це найчастіша причина падіння такого тесту.

### ViewModel: підміна `Dispatchers.Main` і спостереження `Loading`

`SearchViewModel` крутить корутини у `viewModelScope`, тобто на `Dispatchers.Main`, тому:

```kotlin
private val testDispatcher = StandardTestDispatcher()

@BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
@AfterTest fun tearDown() = Dispatchers.resetMain()
```

Стан `Loading` видно тільки якщо fake **справді призупиняється**. Fake без suspend-точки відпрацює в тому ж `runCurrent()`, і тест побачить одразу `Success` — тому ставимо гейт:

```kotlin
private class FakeBookRepository(
    private val result: Result<List<Book>>,
    /** When set, [search] suspends until it completes, so Loading can be observed. */
    private val gate: CompletableDeferred<Unit>? = null,
) : BookRepository {
    var lastQuery: String? = null

    override suspend fun search(query: String, limit: Int): List<Book> {
        lastQuery = query
        gate?.await()
        return result.getOrThrow()
    }
}

@Test
fun emitsLoadingThenSuccess() = runTest(testDispatcher) {
    val gate = CompletableDeferred<Unit>()
    val viewModel = viewModelWith(FakeBookRepository(Result.success(listOf(book("/works/OL1W"))), gate))

    viewModel.onQueryChange("dune")
    viewModel.onSearch()
    runCurrent()
    assertIs<SearchUiState.Status.Loading>(viewModel.state.value.status)

    gate.complete(Unit)
    testScheduler.advanceUntilIdle()
    assertIs<SearchUiState.Status.Success>(viewModel.state.value.status)
}
```

Стан читаємо через `viewModel.state.value` після `advanceUntilIdle()`; окремий колектор `StateFlow` не заводимо.
Клас із `setMain` позначаємо `@OptIn(ExperimentalCoroutinesApi::class)`.

## Запуск

```
./gradlew :server:test
./gradlew :app:shared:testAndroidHostTest
./gradlew build   # включно з компіляцією тестів під js/wasm/ios
```

Тест вважається готовим лише після зеленого прогону — не після написання.
