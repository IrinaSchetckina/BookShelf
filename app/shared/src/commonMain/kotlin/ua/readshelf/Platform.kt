package ua.readshelf

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform