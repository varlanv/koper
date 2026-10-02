package com.varlanv.gradle.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BenchmarkReportTest {
    @Test
    fun `JVM report preserves parameters samples and runtime settings`() {
        val report = BenchmarkReport.parse(reportText(entry(
            "params" to """{"length":"64","content":"ascii"}""",
            "jmhVersion" to "\"1.37\"",
            "jdkVersion" to "\"26\"",
            "vmName" to "\"OpenJDK\"",
            "jvmArgs" to """["-Xmx1g"]""",
            "forks" to "1",
        )))

        assertEquals(mapOf("content" to "ascii", "length" to "64"), report.rows.single().params)
        assertEquals(3, report.rows.single().samples)
        assertContains(report.renderConsole("jvm"), "content=ascii, length=64")
        val html = report.renderHtml("jvm", "quick", "jvm.json")
        assertContains(html, "jdkVersion: 26")
        assertContains(html, "jvmArgs: [&quot;-Xmx1g&quot;]")
        assertContains(html, "href=\"jvm.json\"")
        assertContains(html, "Filter results")
    }

    @Test
    fun `JS report uses the same table with compilation settings`() {
        val report = BenchmarkReport.parse(reportText(entry(
            "compilationMode" to "\"production\"",
            "configurationName" to "\"quick\"",
            "advanced" to """{"jsUseBridge":"false"}""",
        )))

        assertContains(report.renderHtml("js", "quick", "js.json"), "compilationMode: production")
        assertContains(report.renderConsole("js"), "js benchmark results (1 rows)")
        assertEquals(2.0, report.rows.single().score)
        assertEquals(0.5, report.rows.single().error)
        assertContains(report.renderHtml("js", "quick", "js.json", mapOf("nodeVersion" to "v24.14.1")), "nodeVersion: v24.14.1")
    }

    @Test
    fun `one-sample JMH smoke accepts quoted NaN uncertainty`() {
        val report = BenchmarkReport.parse(reportText(entry(
            "measurementIterations" to "1",
            "primaryMetric" to metric("\"NaN\"", "[\"NaN\",\"NaN\"]", "[[2]]"),
        )))

        assertNull(report.rows.single().error)
        assertContains(report.renderConsole("jvm"), "unavailable")
    }

    @Test
    fun `one-sample runner accepts bare NaN uncertainty`() {
        val text = reportText(entry(
            "measurementIterations" to "1",
            "primaryMetric" to metric("\"NaN\"", "[\"NaN\",\"NaN\"]", "[[2]]"),
        )).replace("\"NaN\"", "NaN")

        assertNull(BenchmarkReport.parse(text).rows.single().error)
    }

    @Test
    fun `unparameterized JMH results may omit params`() {
        val row = entry().toMutableMap().apply { remove("params") }
        assertEquals(emptyMap(), BenchmarkReport.parse(reportText(JsonObject(row))).rows.single().params)
    }

    @Test
    fun `multiple forks and nonforked JVM reports are accepted`() {
        assertEquals(6, BenchmarkReport.parse(reportText(entry(
            "forks" to "2",
            "primaryMetric" to metric(raw = "[[1,2,3],[1,2,3]]"),
        ))).rows.single().samples)
        assertEquals(3, BenchmarkReport.parse(reportText(entry("forks" to "0"))).rows.single().samples)
    }

    @Test
    fun `empty malformed duplicate and invalid metrics are rejected`() {
        val invalid = listOf(
            "[]", "{}", "not json", reportText(entry(), entry()),
            reportText(entry("benchmark" to "\"\"")),
            reportText(entry("primaryMetric" to "{}")),
            reportText(entry("primaryMetric" to metric().replace("\"score\":2", "\"score\":0"))),
            reportText(entry("primaryMetric" to metric().replace("\"score\":2", "\"score\":\"Infinity\""))),
            reportText(entry("primaryMetric" to metric(error = "-1"))),
            reportText(entry("primaryMetric" to metric(error = "\"NaN\""))),
            reportText(entry("primaryMetric" to metric(confidence = "[3,4]"))),
            reportText(entry("primaryMetric" to metric(raw = "[]"))),
            reportText(entry("primaryMetric" to metric(raw = "[[]]"))),
            reportText(entry("primaryMetric" to metric(raw = "[[1,\"NaN\",3]]"))),
        )
        invalid.forEach { text ->
            assertFailsWith<IllegalArgumentException>(text) { BenchmarkReport.parse(text) }
        }
    }

    @Test
    fun `incomplete iteration and fork counts are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BenchmarkReport.parse(reportText(entry("measurementIterations" to "4")))
        }
        assertFailsWith<IllegalArgumentException> {
            BenchmarkReport.parse(reportText(entry("forks" to "2")))
        }
    }

    @Test
    fun `HTML escapes results metadata configuration and link filename`() {
        val malicious = "<script>alert('x')</script>"
        val report = BenchmarkReport.parse(reportText(entry(
            "benchmark" to JsonPrimitive(malicious).toString(),
            "params" to JsonObject(mapOf("name" to JsonPrimitive(malicious))).toString(),
            "vmName" to JsonPrimitive(malicious).toString(),
        )))
        val html = report.renderHtml(malicious, malicious, "x\"onmouseover=\"alert.json")

        assertFalse(html.contains(malicious))
        assertContains(html, "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
        assertContains(html, "href=\"x&quot;onmouseover=&quot;alert.json\"")
        assertFailsWith<IllegalArgumentException> { report.renderHtml("js", "quick", "javascript:alert(1)") }
    }

    @Test
    fun `console neutralizes embedded control characters`() {
        val report = BenchmarkReport.parse(reportText(entry(
            "benchmark" to JsonPrimitive("example.\u001B[31m\nbenchmark").toString(),
        )))
        val output = report.renderConsole("js")
        assertFalse('\u001B' in output)
        assertContains(output, "example. [31m benchmark")
    }

    private fun metric(error: String = "0.5", confidence: String = "[1.5,2.5]", raw: String = "[[1,2,3]]"): String =
        """{"score":2,"scoreError":$error,"scoreConfidence":$confidence,"scoreUnit":"ns/op","rawData":$raw}"""

    private fun entry(vararg changes: Pair<String, String>): JsonObject {
        val entry = Json.parseToJsonElement("""{
            "benchmark":"example.Benchmark.method","mode":"avgt","params":{},
            "measurementIterations":3,"primaryMetric":${metric()}
        }""") as JsonObject
        return JsonObject(entry.toMutableMap().apply {
            changes.forEach { (key, value) -> put(key, Json.parseToJsonElement(value)) }
        })
    }

    private fun reportText(vararg entries: JsonObject): String = JsonArray(entries.toList()).toString()
}
