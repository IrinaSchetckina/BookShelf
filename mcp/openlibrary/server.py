from fastmcp import FastMCP
import httpx

mcp = FastMCP("openlibrary")

@mcp.tool
def search_books(query: str, limit: int = 10) -> list[dict]:
    """Пошук книг в Open Library. Повертає лише мінімум:
    key, title, authors, year, coverUrl. Використовуй, коли треба
    справжні дані книги (напр. для тестових фікстур), а не вигадані."""
    limit = max(1, min(limit, 20))
    r = httpx.get(
        "https://openlibrary.org/search.json",
        params={"q": query, "limit": limit,
                "fields": "key,title,author_name,first_publish_year,cover_i"},
        timeout=10,
    )
    r.raise_for_status()
    result = []
    for d in r.json().get("docs", []):
        cover = d.get("cover_i")
        result.append({
            "key": d.get("key"),
            "title": d.get("title"),
            "authors": d.get("author_name", []),
            "year": d.get("first_publish_year"),
            "coverUrl": f"https://covers.openlibrary.org/b/id/{cover}-M.jpg" if cover else None,
        })
    return result

if __name__ == "__main__":
    mcp.run()
