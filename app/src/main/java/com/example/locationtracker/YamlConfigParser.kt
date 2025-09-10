package com.example.locationtracker

import org.yaml.snakeyaml.Yaml

class YamlConfigParser : ConfigParser {
    override fun parse(content: String): AppConfig {
        val yaml = Yaml()
        val yamlMap: Map<String, Any> = yaml.load(content)
        val interval = (yamlMap["loggingIntervalMinutes"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("Missing or invalid 'loggingIntervalMinutes' in config")
        return AppConfig(loggingIntervalMinutes = interval)
    }
}
