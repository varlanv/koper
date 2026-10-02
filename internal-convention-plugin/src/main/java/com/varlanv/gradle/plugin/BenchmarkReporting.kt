package com.varlanv.gradle.plugin

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

internal fun configureBenchmarkReporting(task: Task, buildDirectory: File) {
    val args = when (task) {
        is JavaExec -> task.args
        is NodeJsExec -> task.args
        else -> return
    }
    val configurationFile = File(args.single())
    task.extensions.extraProperties.set("idea.internal.test", false)
    task.outputs.upToDateWhen { false }
    task.actions.add(task.actions.lastIndex, PrepareBenchmarkReport(configurationFile, buildDirectory))
    task.doLast(FinishBenchmarkReport(configurationFile))
}

private class PrepareBenchmarkReport(
    private val configurationFile: File,
    private val buildDirectory: File,
) : Action<Task> {
    override fun execute(task: Task) {
        val configuration = BenchmarkRunConfiguration.read(configurationFile)
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH.mm.ss.SSS"))
        val directory = File(
            buildDirectory,
            "reports/benchmarks/${configuration.profile}/$timestamp-${UUID.randomUUID().toString().take(8)}"
        )
        check(directory.mkdirs()) { "Cannot create benchmark report directory: $directory" }
        val report = File(directory, "${configuration.platform}.json")
        configurationFile.writeText(configurationFile.readLines().joinToString("\n", postfix = "\n") { line ->
            when (line.substringBefore(':')) {
                "traceFormat" -> "traceFormat:xml"
                "reportFormat" -> "reportFormat:json"
                "reportFile" -> "reportFile:${report.absolutePath}"
                else -> line
            }
        })
        if (task is NodeJsExec) {
            val process = ProcessBuilder(task.executable, "--version").redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw GradleException("Cannot determine the Node.js version")
            }
            check(process.exitValue() == 0) { "Cannot determine the Node.js version" }
            val version = process.inputStream.bufferedReader().use { it.readText().trim() }
            File(directory, "runtime.txt").writeText("nodeExecutable:${task.executable}\nnodeVersion:$version\n")
        }
        val stdout = File(directory, "events.log").outputStream().buffered()
        val stderr = File(directory, "stderr.log").outputStream().buffered()
        when (task) {
            is JavaExec -> {
                task.standardOutput = stdout
                task.errorOutput = stderr
            }

            is NodeJsExec -> {
                task.standardOutput = stdout
                task.errorOutput = stderr
            }
        }
        task.logger.lifecycle("Benchmark logs: ${directory.toPath().toUri()}")
    }
}

private class FinishBenchmarkReport(private val configurationFile: File) : Action<Task> {
    override fun execute(task: Task) {
        val configuration = BenchmarkRunConfiguration.read(configurationFile)
        val reportFile = File(configuration.reportFile)
        val directory = reportFile.parentFile
        val trace = File(directory, "events.log").takeIf(File::exists)?.readText().orEmpty()
        val stderr = File(directory, "stderr.log").takeIf(File::exists)?.readText().orEmpty()
        val logFile = File(directory, "runner.log")
        val logText = BenchmarkTrace.readable(trace) + stderr
        logFile.writeText(logText)
        try {
            require(reportFile.isFile) { "The runner did not write a results file" }
            val report = BenchmarkReport.parse(reportFile.readText())
            BenchmarkTrace.validate(trace, report.rows.size)
            val htmlFile = File(directory, "${configuration.platform}.html")
            val runtimeMetadata = File(directory, "runtime.txt").takeIf(File::exists)?.readLines()
                ?.associate { it.substringBefore(':') to it.substringAfter(':') }.orEmpty()
            htmlFile.writeText(
                report.renderHtml(
                    configuration.platform,
                    configuration.profile,
                    reportFile.name,
                    runtimeMetadata
                )
            )
            task.logger.lifecycle(report.renderConsole(configuration.platform))
            task.logger.lifecycle("Benchmark report: ${htmlFile.toPath().toUri()}")
            task.logger.lifecycle("Benchmark JSON: ${reportFile.toPath().toUri()}")
        } catch (exception: Exception) {
            val details = BenchmarkTrace.failureDetails(logText)
            throw GradleException(
                "Invalid benchmark run: ${exception.message}\nLogs: ${
                    logFile.toPath().toUri()
                }\n$details", exception
            )
        }
    }
}

internal object BenchmarkTrace {
    private val event = Regex("<ijLog>.*?</ijLog>", RegexOption.DOT_MATCHES_ALL)
    private val testId = Regex("<test id='(.*?)' parentId=", RegexOption.DOT_MATCHES_ALL)
    private val output = Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)
    private val exceptionLine = Regex("""^(?:Caused by:\s*)?(?:[\w$]+\.)*[\w$]*(?:Exception|Error)(?::.*)?$""")

    fun failureDetails(log: String): String {
        val lines = log.lineSequence().filter(String::isNotBlank).toList()
        val firstException = lines.indexOfFirst { exceptionLine.matches(it.trim()) }
        return (if (firstException >= 0) lines.drop(firstException).take(12) else lines.takeLast(12))
            .joinToString("\n")
    }

    fun validate(trace: String, rowCount: Int) {
        val events = event.findAll(trace).map { it.value }.toList()
        require(events.none { it.contains("resultType='FAILURE'") }) { "The runner reported a benchmark failure" }
        val starts = events.filter { it.contains("type='beforeTest'") }.map { testId.find(it)!!.groupValues[1] }.toSet()
        val finishes = events.filter { it.contains("type='afterTest'") && it.contains("resultType='SUCCESS'") }
            .map { testId.find(it)!!.groupValues[1] }.toSet()
        require(starts.isNotEmpty() && starts == finishes) { "Some benchmarks did not finish successfully" }
        require(starts.size == rowCount) { "Results are incomplete: ${starts.size} benchmarks ran, $rowCount results were written" }
        require(events.any { it.contains("type='afterSuite'") && it.contains("id='[root]'") }) { "The benchmark suite did not finish" }
    }

    fun readable(trace: String): String = event.replace(trace) { match ->
        if (!match.value.contains("type='onOutput'")) ""
        else output.find(match.value)?.groupValues?.get(1)?.let { encoded ->
            runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrDefault(encoded)
        }.orEmpty()
    }
}

private data class BenchmarkRunConfiguration(val platform: String, val profile: String, val reportFile: String) {
    companion object {
        fun read(file: File): BenchmarkRunConfiguration {
            val entries = file.readLines().associate { it.substringBefore(':') to it.substringAfter(':') }
            return BenchmarkRunConfiguration(
                entries.getValue("name"),
                entries.getValue("configurationName"),
                entries.getValue("reportFile")
            )
        }
    }
}
