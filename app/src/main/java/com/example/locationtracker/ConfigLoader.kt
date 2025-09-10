package com.example.locationtracker

import android.content.Context
import java.io.IOException

class ConfigLoader(private val context: Context) {

    fun loadConfig(fileName: String = "config.yaml"): AppConfig {
        val content = try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IOException("Failed to read config file: $fileName", e)
        }

        val parser = when {
            fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> YamlConfigParser()
            // In the future, you could add more parsers here
            // fileName.endsWith(".json") -> JsonConfigParser()
            else -> throw IllegalArgumentException("Unsupported config file type: $fileName")
        }

        return parser.parse(content)
    }
}
