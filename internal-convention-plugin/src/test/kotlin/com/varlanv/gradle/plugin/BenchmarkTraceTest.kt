package com.varlanv.gradle.plugin

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains
import kotlin.test.assertFalse

class BenchmarkTraceTest {
    @Test
    fun acceptsCompleteRunAndRepeatedForkEvents() {
        BenchmarkTrace.validate(start("one") + finish("one") + start("one") + finish("one") + suiteEnd, 1)
    }

    @Test
    fun rejectsFailureEvenWithPartialResultsAndSuccessfulSuite() {
        assertFailsWith<IllegalArgumentException> {
            BenchmarkTrace.validate(start("one") + finish("one") + start("two") + finish("two", "FAILURE") + suiteEnd, 1)
        }
    }

    @Test
    fun rejectsMissingResult() {
        assertFailsWith<IllegalArgumentException> { BenchmarkTrace.validate(start("one") + finish("one") + suiteEnd, 0) }
    }

    @Test
    fun rejectsUnfinishedBenchmarkAndSuite() {
        assertFailsWith<IllegalArgumentException> { BenchmarkTrace.validate(start("one") + suiteEnd, 1) }
        assertFailsWith<IllegalArgumentException> { BenchmarkTrace.validate(start("one") + finish("one"), 1) }
        assertFailsWith<IllegalArgumentException> { BenchmarkTrace.validate("", 1) }
    }

    @Test
    fun decodesOutputAndPreservesWarnings() {
        val message = "Привіт: 42 ns/op\n"
        val encoded = Base64.getEncoder().encodeToString(message.toByteArray())
        val trace = "warning\n" + start("one") + "<ijLog><event type='onOutput'><![CDATA[$encoded]]></event></ijLog>" + finish("one")
        assertEquals("warning\n$message", BenchmarkTrace.readable(trace))
    }

    @Test
    fun showsSetupExceptionInsteadOfTrailingSummary() {
        val log = """
            <failure>

            java.lang.ClassNotFoundException: example.MissingCompanion
                at example.Benchmark.setup(Benchmark.kt:30)
            ${"summary\n".repeat(20)}
        """.trimIndent()
        val details = BenchmarkTrace.failureDetails(log)
        assertContains(details, "java.lang.ClassNotFoundException: example.MissingCompanion")
        assertContains(details, "at example.Benchmark.setup")
    }

    @Test
    fun showsJsExceptionAndKeepsFallbackDiagnostics() {
        val log = "TypeError: invalid argument\n" + "summary\n".repeat(20)
        assertContains(BenchmarkTrace.failureDetails(log), "TypeError: invalid argument")
        assertEquals("No benchmarks to run", BenchmarkTrace.failureDetails("\nNo benchmarks to run\n"))
        assertFalse(BenchmarkTrace.failureDetails(log).startsWith("summary"))
    }

    private fun start(id: String) = "<ijLog><event type='beforeTest'><test id='$id' parentId='suite'/></event></ijLog>"

    private fun finish(id: String, status: String = "SUCCESS") =
        "<ijLog><event type='afterTest'><test id='$id' parentId='suite'><result resultType='$status'/></test></event></ijLog>"

    private val suiteEnd = "<ijLog><event type='afterSuite'><test id='[root]' parentId=''><result resultType='SUCCESS'/></test></event></ijLog>"
}
