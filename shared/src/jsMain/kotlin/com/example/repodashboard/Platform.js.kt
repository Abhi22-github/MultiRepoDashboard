package com.example.repodashboard

import web.navigator.navigator

class JsPlatform: Platform {
    private val userAgent = navigator.userAgent
    private val browserList = listOf("Chrome", "Firefox", "Safari", "Edge")

    override val name: String = userAgent.findAnyOf(browserList, ignoreCase = true)
            ?.let { (startIndex) -> userAgent.substring(startIndex).substringBefore(" ") }
            ?: "Unknown"
}

actual fun getPlatform(): Platform = JsPlatform()
actual fun openUrl(url: String) { kotlinx.browser.window.open(url, "_blank") }
actual fun prefersReducedMotion(): Boolean = kotlinx.browser.window.matchMedia("(prefers-reduced-motion: reduce)").matches
