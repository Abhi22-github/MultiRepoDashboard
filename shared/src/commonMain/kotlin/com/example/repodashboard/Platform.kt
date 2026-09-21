package com.example.repodashboard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun openUrl(url: String)