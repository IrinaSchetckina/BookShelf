package com.shchotkina.readshelf

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform