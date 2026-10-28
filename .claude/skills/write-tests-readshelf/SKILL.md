---
name: write-tests-readshelf
description: Інструменти й конвенції тестів саме в ReadShelf — Ktor testApplication, MockEngine, coroutines-test, команди запуску. Читати разом зі скілом spec-guard:write-tests перед написанням або правкою будь-якого тесту.
---

# Тести ReadShelf: інструменти й конвенції

Принципи — що покривати, як називати, як закладати тестованість у продакшн-код — у скілі
**`spec-guard:write-tests`**. Прочитай його першим. Тут лише те, чим ми це робимо в цьому репозиторії.

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

## Конвенції цього репозиторію

- **Імена тестів:** у `:server` (JVM) — backticks з описом поведінки:
  ``fun `rejects a blank query with 400`()``. У `commonTest` backticks **заборонені**
  (js/wasm/native) — camelCase: `emitsLoadingThenSuccess()`.
- **Тестованість уже закладена:** `HttpClient` і `baseUrl` — параметри конструктора з дефолтами
  (`OpenLibraryClient`, `ReadShelfApi`); `Application.module()` приймає `OpenLibraryClient`.
- **Дані книг для фікстур** — за скілом `book-fixtures` (реальні ключі з Open Library, не вигадані).

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
Без заголовка `Content-Type: application/json` ContentNegotiation не розпарсить тіло — це
найчастіша причина падіння такого тесту.

### ViewModel: підміна `Dispatchers.Main` і спостереження `Loading`

`SearchViewModel` крутить корутини у `viewModelScope`, тобто на `Dispatchers.Main`, тому:

```kotlin
private val testDispatcher = StandardTestDispatcher()

@BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
@AfterTest fun tearDown() = Dispatchers.resetMain()
```

Стан `Loading` видно тільки якщо fake **справді призупиняється** — ставимо гейт:

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
```

Стан читаємо через `viewModel.state.value` після `advanceUntilIdle()`; окремий колектор
`StateFlow` не заводимо. Клас із `setMain` позначаємо `@OptIn(ExperimentalCoroutinesApi::class)`.

## Запуск

```
./gradlew :server:test
./gradlew :core:jvmTest
./gradlew :app:shared:testAndroidHostTest   # jvm-таргету в :app:shared немає
./gradlew build                             # включно з компіляцією тестів під js/wasm/ios
```

Тести читання (`:core` домен + `:app:shared`) ганяє автоматично гейт зі `.claude/gates.json`
після правок і перед кінцем ходу — див. плагін `spec-guard`.
