package com.varlanv.gradle.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

internal data class BenchmarkReportRow(
    val benchmark: String,
    val mode: String,
    val params: Map<String, String>,
    val score: Double,
    val error: Double?,
    val unit: String,
    val rawData: List<List<Double>>,
    val metadata: Map<String, String>,
) {
    val samples: Int get() = rawData.sumOf { it.size }
}

internal class BenchmarkReport private constructor(val rows: List<BenchmarkReportRow>) {
    fun renderConsole(platform: String): String {
        val header = listOf("Benchmark", "Parameters", "Mode", "Samples", "Score", "Error", "Units")
        val values = rows.map { row ->
            listOf(
                row.benchmark.split('.').takeLast(2).joinToString("."), row.params.parameterText(), row.mode, row.samples.toString(),
                row.score.formatted(), row.error?.formatted() ?: "unavailable", row.unit,
            ).map { it.consoleText() }
        }
        val widths = header.indices.map { column -> maxOf(header[column].length, values.maxOf { it[column].length }) }
        return buildString {
            appendLine("${platform.consoleText()} benchmark results (${rows.size} rows)")
            appendLine(header.mapIndexed { index, value -> value.padEnd(widths[index]) }.joinToString("  ").trimEnd())
            values.forEach { row ->
                appendLine(row.mapIndexed { index, value -> value.padEnd(widths[index]) }.joinToString("  ").trimEnd())
            }
        }.trimEnd()
    }

    fun renderHtml(platform: String, configuration: String, jsonFileName: String, runtimeMetadata: Map<String, String> = emptyMap()): String {
        require(jsonFileName.isNotBlank() && '/' !in jsonFileName && '\\' !in jsonFileName && ':' !in jsonFileName) {
            "The raw JSON link must be a local filename"
        }
        val metadata = rows.map { it.metadata + runtimeMetadata }.distinct()
        return buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("<title>${platform.htmlText()} · ${configuration.htmlText()} benchmarks</title>")
            appendLine("""<style>
                :root{color-scheme:light dark;font-family:system-ui,sans-serif}body{margin:2rem;max-width:1600px}h1{margin-bottom:.5rem}a{color:light-dark(#1554ad,#8cbcff)}input{font:inherit;padding:.65rem;width:min(30rem,90%);margin:1rem 0}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:.7rem;border-bottom:1px solid #8885;vertical-align:top}th{white-space:nowrap}td.number{text-align:right;font-variant-numeric:tabular-nums}td.name{overflow-wrap:anywhere}caption{text-align:left;padding:.5rem 0}details{margin-top:1rem}pre{white-space:pre-wrap;overflow-wrap:anywhere}small{opacity:.8}
                </style></head><body>""".trimIndent())
            appendLine("<h1>${platform.htmlText()} benchmarks</h1>")
            appendLine("<p>Configuration: <strong>${configuration.htmlText()}</strong> · <a href=\"${jsonFileName.htmlText()}\">Raw JSON</a> · <a href=\"runner.log\">Runner log</a></p>")
            appendLine("<label for=\"search\">Filter results</label><br><input id=\"search\" type=\"search\" placeholder=\"Benchmark, parameters, mode or units\" autocomplete=\"off\">")
            appendLine("<div class=\"table-wrap\"><table><caption id=\"count\">${rows.size} results</caption><thead><tr><th>Platform</th><th>Benchmark</th><th>Parameters</th><th>Mode</th><th>Samples</th><th>Score</th><th>Error</th><th>Units</th></tr></thead><tbody>")
            rows.forEach { row ->
                append("<tr><td>${platform.htmlText()}</td><td class=\"name\">${row.benchmark.htmlText()}</td>")
                append("<td>${row.params.parameterText().htmlText()}</td><td>${row.mode.htmlText()}</td>")
                append("<td class=\"number\">${row.samples}</td><td class=\"number\">${row.score.formatted()}</td>")
                appendLine("<td class=\"number\">${row.error?.let { "± ${it.formatted()}" } ?: "unavailable"}</td><td>${row.unit.htmlText()}</td></tr>")
            }
            appendLine("</tbody></table></div><p><small>Error is the margin reported by each runner. JVM and JS use different statistical methods. Unavailable means too few samples or no reported error.</small></p>")
            metadata.forEachIndexed { index, values ->
                val title = if (metadata.size == 1) "Runtime and measurement settings" else "Runtime and measurement settings ${index + 1}"
                appendLine("<details><summary>$title</summary><pre>${values.entries.joinToString("\n") { "${it.key}: ${it.value}" }.htmlText()}</pre></details>")
            }
            appendLine("""<script>
                const search=document.getElementById('search');const rows=Array.from(document.querySelectorAll('tbody tr'));const count=document.getElementById('count');search.addEventListener('input',()=>{const query=search.value.toLowerCase();let visible=0;for(const row of rows){row.hidden=!row.textContent.toLowerCase().includes(query);if(!row.hidden)visible++;}count.textContent=visible+' of '+rows.length+' results';});
                </script></body></html>""".trimIndent())
        }
    }

    companion object {
        private val json = Json { allowSpecialFloatingPointValues = true }
        private val metadataKeys = listOf(
            "jmhVersion", "compilationMode", "configurationName", "jdkVersion", "vmName", "vmVersion", "jvm", "jvmArgs",
            "threads", "forks", "warmupIterations", "warmupTime", "warmupBatchSize", "measurementIterations",
            "measurementTime", "measurementBatchSize", "advanced",
        )

        fun parse(text: String): BenchmarkReport {
            val entries = try {
                json.parseToJsonElement(text) as? JsonArray ?: error("Benchmark report must be a JSON array")
            } catch (exception: Exception) {
                throw IllegalArgumentException("Invalid benchmark report: ${exception.message}", exception)
            }
            require(entries.isNotEmpty()) { "Benchmark report has no results" }
            val rows = entries.mapIndexed { index, element ->
                try {
                    parseRow(element as? JsonObject ?: error("Result must be an object"))
                } catch (exception: Exception) {
                    throw IllegalArgumentException("Invalid benchmark result ${index + 1}: ${exception.message}", exception)
                }
            }
            val identities = rows.map { Triple(it.benchmark, it.mode, it.params) }
            require(identities.distinct().size == identities.size) { "Benchmark report contains duplicate results" }
            return BenchmarkReport(rows)
        }

        private fun parseRow(entry: JsonObject): BenchmarkReportRow {
            val benchmark = entry.requiredString("benchmark")
            val mode = entry.requiredString("mode")
            val params = when (val value = entry["params"]) {
                null -> emptyMap()
                is JsonObject -> value.mapValues { (key, param) ->
                    require(param is JsonPrimitive && param !== JsonNull) { "Parameter $key must be a scalar" }
                    param.content
                }.toSortedMap()
                else -> error("params must be an object")
            }
            val metric = entry["primaryMetric"] as? JsonObject ?: error("Missing primaryMetric object")
            val score = metric.requiredNumber("score")
            require(score.isFinite() && score > 0) { "score must be finite and positive" }
            val unit = metric.requiredString("scoreUnit")
            val raw = metric["rawData"] as? JsonArray ?: error("Missing rawData array")
            require(raw.isNotEmpty()) { "rawData has no forks" }
            val rawData = raw.map { fork ->
                val samples = fork as? JsonArray ?: error("Each rawData fork must be an array")
                require(samples.isNotEmpty()) { "rawData contains an empty fork" }
                samples.map { sample ->
                    val number = sample.number("rawData sample")
                    require(number.isFinite() && number >= 0) { "rawData samples must be finite and nonnegative" }
                    number
                }
            }
            entry["measurementIterations"]?.let { value ->
                val iterations = value.positiveInteger("measurementIterations")
                require(rawData.all { it.size == iterations }) { "rawData is incomplete: expected $iterations samples per fork" }
            }
            entry["forks"]?.let { value ->
                val forks = value.nonnegativeInteger("forks").coerceAtLeast(1)
                require(rawData.size == forks) { "rawData is incomplete: expected $forks forks" }
            }
            val count = rawData.sumOf { it.size }
            val error = metric["scoreError"]?.let { value ->
                val number = value.number("scoreError")
                require((number.isFinite() && number >= 0) || (number.isNaN() && count <= 2)) {
                    "scoreError must be finite and nonnegative, or unavailable with at most two samples"
                }
                number.takeIf { it.isFinite() }
            }
            metric["scoreConfidence"]?.let { value ->
                val interval = value as? JsonArray ?: error("scoreConfidence must be an array")
                require(interval.size == 2) { "scoreConfidence must contain two bounds" }
                val bounds = interval.map { it.number("scoreConfidence") }
                require(bounds.all { it.isFinite() } || (count <= 2 && bounds.all { it.isNaN() })) {
                    "scoreConfidence must contain finite bounds, or unavailable bounds with at most two samples"
                }
                require(!bounds.all { it.isFinite() } || bounds[0] <= score && score <= bounds[1]) {
                    "scoreConfidence must contain score"
                }
            }
            val metadata = metadataKeys.mapNotNull { key -> entry[key]?.let { key to it.metadataText() } }.toMap()
            return BenchmarkReportRow(benchmark, mode, params, score, error, unit, rawData, metadata)
        }

        private fun JsonObject.requiredString(key: String): String {
            val value = this[key] as? JsonPrimitive ?: error("Missing $key string")
            require(value.isString && value.content.isNotBlank()) { "$key must be a nonempty string" }
            return value.content
        }

        private fun JsonObject.requiredNumber(key: String): Double = this[key]?.number(key) ?: error("Missing $key number")

        private fun JsonElement.number(name: String): Double =
            (this as? JsonPrimitive)?.content?.toDoubleOrNull() ?: error("$name must be a number")

        private fun JsonElement.nonnegativeInteger(name: String): Int {
            val value = (this as? JsonPrimitive)?.content?.toIntOrNull() ?: error("$name must be an integer")
            require(value >= 0) { "$name must be nonnegative" }
            return value
        }

        private fun JsonElement.positiveInteger(name: String): Int = nonnegativeInteger(name).also {
            require(it > 0) { "$name must be positive" }
        }

        private fun JsonElement.metadataText(): String = when (this) {
            is JsonPrimitive -> content
            else -> toString()
        }
    }
}

private fun Map<String, String>.parameterText(): String = entries.joinToString(", ") { "${it.key}=${it.value}" }.ifEmpty { "—" }

private fun Double.formatted(): String = String.format(Locale.ROOT, "%.6g", this)

private fun String.consoleText(): String = map { if (it.isISOControl()) ' ' else it }.joinToString("")

private fun String.htmlText(): String = buildString {
    this@htmlText.forEach { character ->
        append(when (character) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            else -> character.toString()
        })
    }
}
