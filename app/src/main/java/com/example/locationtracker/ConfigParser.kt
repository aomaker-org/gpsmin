package com.example.locationtracker

interface ConfigParser {
    fun parse(content: String): AppConfig
}

data class AppConfig(
    val loggingIntervalMinutes: Long
)
