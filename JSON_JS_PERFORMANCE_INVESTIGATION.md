# JSON performance investigation on Kotlin/JS

Date: 2026-09-30.

## Findings

Three concrete costs were identified in the generated JavaScript:

1. The streaming `StringSource` encodes large Unicode inputs in a JavaScript loop. It removes an intermediate byte allocation and copy, but replaces the previous native `TextEncoder` fast path. Encoding accounts for about 74% of the measured large-read CPU samples.
2. Reader and writer share a byte-store helper. Reader warmup changes V8's optimization of that helper: the slow version calls `DataViewPrototypeSetInt8` for every byte, while the fast version performs a direct machine-code byte store. This explains the large write regression despite an unchanged write algorithm.
3. Generated write sizing and reservation checks still use emulated Kotlin `Long` arithmetic. Instrumentation counted 23 emitted Long constructor executions per write. Removing only this arithmetic approximately halved small-write time.

kotlinx.serialization does not call `JSON.parse` through the measured `decodeFromString` API. Its advantage on large unescaped strings comes from operating on the original JavaScript string and returning a substring view, avoiding UTF-8 conversion and a full copy of the field's characters.

The recommended next changes are checked Int sizing and reservation, a native `TextEncoder.encodeInto` path for large JS string inputs, and independent or inlined byte-store paths for reading and writing. Direct string parsing is a larger subsequent change.

## State of the repository during this investigation

Changes already implemented and tested:

- JSON runtime and KSP-generated packed operations use at most Int SWAR instead of Long SWAR.
- Int JSON codecs use Int arithmetic rather than delegating Int parsing and sizing to Long arithmetic.
- `StringSource` streams UTF-8 bytes into the parser's supplied buffer, with an ASCII fast path and pending bytes for partially written code points.
- The JS benchmark compares Koper, native JSON, and kotlinx.serialization using String fields.

The performance experiments described below changed generated modules in memory in isolated Node processes. The investigation did not implement the proposed native encoder, setter isolation, or Int sizing fixes in repository source.

Actual Long-valued JSON fields remain supported. Removing avoidable Long arithmetic from buffer management is a separate concern from supporting 64-bit numeric values.

## Environment and benchmark workload

| Setting | Value |
|---|---|
| Node | 24.16.0 |
| Kotlin | 2.4.20 |
| kotlinx.serialization | 1.11.0 |
| kotlinx-benchmark | 0.5.0 |
| JS executable | Production |
| Warmups | 3, one second each |
| Measured iterations | 5, one second each |
| Samples | 16 per shape, generated outside timed calls |
| Random seed | 42 |

The benchmark model has three fields:

```kotlin
data class JsonJsSample(
    val active: Boolean,
    val text: String,
    val sequence: Int,
)
```

Each shape varies the Boolean, the signed Int, and the text. All implementations receive corresponding values from the same sample set.

| Shape | Text |
|---|---|
| ASCII | A short order string with randomized numeric and hexadecimal parts |
| ESCAPED | Quotes, backslashes, newline, tab, and a NUL character, with randomized parts |
| BIG | Unicode text containing Ukrainian, accented Latin, Japanese, and an emoji, repeated 1,024–4,096 times |

The diagnostic BIG samples average approximately 93,697 UTF-16 code units and 124,501 UTF-8 bytes per JSON document. BIG is primarily large, unescaped Unicode text; it is not a large document dominated by escape sequences or many object fields.

Read benchmarks start with a JSON String. Write benchmarks return a JSON String. Koper therefore includes its required UTF-8 adaptation in the timed operation. Native uses a plain JS object; Koper and kotlinx use the same Kotlin model.

Parser scopes and output buffers are reused. Setup verifies every sample twice, including decoded values and matching serialized output. Sample generation and verification are outside the measured methods.

Source: [JsonStreamBenchmark.js.kt](benchmarks/benchmarks-json/src/jsMain/kotlin/com/varlanv/koper/benchmarks/json/JsonStreamBenchmark.js.kt).

## Full benchmark results

Mean time in microseconds per operation. Lower is better. These are full benchmark measurements, not the isolated diagnostic timings in later sections.

### After Int SWAR, before streaming StringSource

Koper encoded the complete JSON input with `Charset.Utf8.allocateByteSlice`, then read it through `ByteArraySource`.

| Shape | Operation | Koper | Native | kotlinx |
|---|---|---:|---:|---:|
| ASCII | Read | 1.001 | 0.219 | 1.545 |
| ASCII | Write | 0.505 | 0.143 | 0.583 |
| ESCAPED | Read | 1.284 | 0.262 | 2.010 |
| ESCAPED | Write | 0.568 | 0.170 | 0.934 |
| BIG | Read | 504.082 | 108.434 | 109.404 |
| BIG | Write | 379.101 | 181.889 | 526.649 |

### With streaming StringSource

| Shape | Operation | Koper | Native | kotlinx |
|---|---|---:|---:|---:|
| ASCII | Read | 0.322 | 0.220 | 1.574 |
| ASCII | Write | 0.677 | 0.145 | 0.582 |
| ESCAPED | Read | 0.531 | 0.263 | 2.009 |
| ESCAPED | Write | 0.827 | 0.171 | 0.928 |
| BIG | Read | 947.660 | 110.341 | 109.532 |
| BIG | Write | 873.351 | 180.355 | 486.893 |

Streaming improved ASCII reads by approximately 3.1× and escaped reads by 2.4×, while making BIG reads approximately 1.9× slower. The writer slowdown was subsequently reproduced and traced to shared JIT feedback, rather than a change to the write algorithm.

In the second run, native BIG reads were 110.341 ± 1.204 µs and kotlinx BIG reads were 109.532 ± 0.297 µs. The difference is about 0.7% and the reported uncertainty ranges overlap. These measurements establish near-native performance, not a reliable kotlinx victory over native parsing.

Saved full results: [string-source-js.json](benchmarks/benchmarks-json/build/reports/benchmarks/string-source-js.json). Build artifacts can disappear after cleaning. The tables above preserve the measurements independently of those artifacts.

## Why kotlinx can match native on BIG

### It uses its own parser

The installed kotlinx.serialization 1.11.0 source and generated JavaScript show this path:

```text
Json.decodeFromString
  -> StringJsonLexer
  -> StreamingJsonDecoder
  -> generated Kotlin model serializer
```

There is no native `JSON.parse` call in this path. The separately exposed dynamic-object APIs are not what the benchmark calls.

### Its unescaped-string path stays in JavaScript strings

The relevant `StringJsonLexer.consumeKeyString` logic is:

1. Consume the opening quote.
2. Find the candidate closing quote using `source.indexOf('"', current)`.
3. Scan characters before that quote for a backslash.
4. Fall back to escape handling if a backslash is found.
5. Return `source.substring(current, closingQuote)` for the unescaped case.

The backslash-check loop still visits characters. This is not a claim that kotlinx performs no scan or that every string operation is constant-time. The important differences are the native quote search, the original UTF-16 representation, and the absence of UTF-8 conversion and character copying for the observed substring result.

### V8 returns a view of the original string

V8 inspection of the actual BIG results showed:

| Implementation | Observed V8 string representation |
|---|---|
| kotlinx | `SLICED_TWO_BYTE_STRING_TYPE` |
| Native JSON.parse | `SEQ_TWO_BYTE_STRING_TYPE` |
| Koper after UTF-8 decoding | `SEQ_TWO_BYTE_STRING_TYPE` |

A sliced string retains the source string's backing storage together with an offset and length. A sequential string has its own flat character storage.

kotlinx does not integrate with V8 through a special native binding. It calls ordinary `substring`; V8 chooses the representation internally. JavaScript strings are immutable, so sharing backing storage is safe.

This representation is an observed optimization for this runtime and these inputs, not a portable guarantee that every substring on every engine avoids copying. Escaped strings also follow a different path.

### Why Koper wins small reads

For a short object, the large-string advantages contribute little. Koper's generated field matching and direct primitive reads avoid much of kotlinx's general serializer/decoder machinery.

This explains the direction of the small-object results. The investigation did not separately attribute every nanosecond of kotlinx's small-object overhead to a particular decoder method.

## Koper's read pipeline

With the current adapter:

```text
Original JS JSON String
  -> StringSource encodes code points into the supplied parser buffer
  -> scalar JS byte scanner finds string boundaries and handles escapes
  -> UTF-8 decoder creates the String field
  -> generated codec constructs the Kotlin model
```

Relevant source:

- [StringSource.kt](libs/lang/src/commonMain/kotlin/com/varlanv/koper/lang/bin/StringSource.kt)
- [Charset.js.kt](libs/lang/src/jsMain/kotlin/com/varlanv/koper/lang/text/Charset.js.kt)
- [JsonPlatform.js.kt](libs/json/json-core/src/jsMain/kotlin/com/varlanv/koper/json/JsonPlatform.js.kt)
- [JsonSpecialScan.kt](libs/json/json-core/src/commonMain/kotlin/com/varlanv/koper/json/JsonSpecialScan.kt)
- [JsonStringScanner.kt](libs/json/json-core/src/commonMain/kotlin/com/varlanv/koper/json/JsonStringScanner.kt)
- [JsonCodec.kt](libs/json/json-core/src/commonMain/kotlin/com/varlanv/koper/json/JsonCodec.kt)

The JS scanner backend is scalar. The JVM vector backend is not used by the JS benchmark. Removing packed Long operations improved primitive and field processing, but did not turn the JS bulk string scanner into a vectorized scanner.

### CPU profile of current BIG reads

| Work | Approximate share of CPU samples |
|---|---:|
| StringSource UTF-8 encoding and byte stores | 73.7% |
| String scanning | 6.7% |
| UTF-8 to String decoding | 11.9% |
| Other work | 7.7% |

These are grouped sampling-profile percentages, not independent wall-clock timings that should be added to the separate diagnostic measurements below.

### Encoding and complete-read diagnostics

The diagnostics used the actual generated production modules and the same 16 BIG samples in Node 24.16.0. The timing harness warmed the calls and ran repeated batches in isolated processes. These timings establish where time goes and the size of potential gains; they are not replacement full benchmark results for a production-ready new implementation.

| Diagnostic | µs/op |
|---|---:|
| Current StringSource encoding alone | 721–722 |
| Native TextEncoder.encode alone | 162–166 |
| Native TextEncoder.encodeInto alone | 107–111 |
| Current complete Koper read | About 948 |
| Complete Koper read using a diagnostic native encodeInto adapter | 313–335 |
| Complete Koper read with preencoded input | 206–222 |

The previous `Charset.Utf8.allocateByteSlice` implementation already selected native `TextEncoder` for strings of at least 1,024 characters when their surrogate handling was compatible. Streaming removed allocation and copying, but also removed that native fast path.

The diagnostic `encodeInto` adapter wrote into a `Uint8Array` view of the supplied parser storage and advanced its input position by the returned UTF-16 `read` count. It did not change the JSON parser or Kotlin model construction.

Preencoded-input timing excludes input encoding. It isolates more of the byte parser's cost, but is not an equivalent replacement for the current String-input comparison.

## Shared byte-store JIT feedback

### The observed regression

The writer implementation was unchanged when `StringSource` was introduced, yet BIG writes moved from about 379 µs to 873 µs.

Both `StringSource` and string serialization call the generated helper corresponding to:

```kotlin
actual operator fun set(idx: Int, value: Byte) =
    impl.setInt8(byteOffset = idx, value = value)
```

Source: [MutBytes.js.kt](libs/lang/src/jsMain/kotlin/com/varlanv/koper/lang/bin/MutBytes.js.kt).

Benchmark setup calls both readers and writers. Sharing a helper lets reader calls train the same function that the writer later uses. Running benchmark methods in separate processes does not remove training performed by that process's shared setup.

### Isolated experiments

The generated writer and payloads were held constant:

| Experiment | BIG write, µs/op |
|---|---:|
| Original shared setter after current StringSource setup | 854–863 |
| Native reader used during setup | 380–385 |
| Separate equivalent setter for the writer, original StringSource retained | 376–382 |

The separate-setter experiment changed the equivalent byte-store function in memory. It did not replace the write algorithm or remove StringSource encoding.

### Machine-code evidence

After the default reader training, the optimized setter includes a builtin call:

```text
call ... (DataViewPrototypeSetInt8)
```

With native reader training, the optimized helper instead includes a direct byte store:

```text
movb [rcx+r8*1],dil
```

The trace also shows an early setter deoptimization labelled `out of bounds`. Its exact triggering circumstances were not fully established. That label alone is not evidence that the parser performs an invalid application-level write.

The experiments establish that shared feedback changes generated machine code and write throughput. They do not establish that every possible inlining or wrapper change will fix it, or that all V8 versions make the same decisions.

Potential fixes to measure are independent reader/writer byte-store sites, inlining the byte access into callers, or JS-specific typed-array access. A wrapper that still calls the same shared helper may retain the original problem.

## Remaining Long arithmetic

The Long SWAR conversion did not remove all Long operations. Runtime size calculations and even comparisons still use emulated Long arithmetic in JS.

### Steady-state generated write: 23 constructor executions

The generated size expression for this benchmark model is:

```kotlin
49L + value.text.length.toLong() * 6L
```

It is passed to `JsonWriteProtocol.reserve(Long)`, which validates the range and delegates to `reserve(Int)`.

Sources:

- [JsonSerdeGenerator.kt](libs/json/json-ksp/src/main/kotlin/com/varlanv/koper/json/ksp/JsonSerdeGenerator.kt)
- [JsonWriteProtocol.kt](libs/json/json-core/src/commonMain/kotlin/com/varlanv/koper/json/JsonWriteProtocol.kt)

Instrumenting the generated stdlib Long constructor counted 23 executions for each of the 16 samples in ASCII, ESCAPED, and BIG:

| Operation | Long constructor executions |
|---|---:|
| Two literal Long values in maximumBytes | 2 |
| Convert text length to Long | 1 |
| Two internal multiplication comparisons | 8 |
| Multiplication result | 1 |
| Addition result | 1 |
| **maximumBytes subtotal** | **13** |
| Two Long bounds in reserve | 2 |
| Two reservation comparisons | 8 |
| **reserve subtotal** | **10** |
| **Total per generated write** | **23** |

The generated Long comparison calls subtraction. Subtraction uses negation and addition, and the observed comparison path executes four Long constructors. Comparisons are therefore not free numeric comparisons in this generated code. Conversion back to Int reads the low word and does not itself construct a Long.

The count is constructor execution in instrumented generated JavaScript. Instrumentation affects optimization, and V8 may eliminate some allocations in an uninstrumented run. It is not a claim that all 23 objects necessarily survive as separate heap allocations.

Warmed reads of the current benchmark model executed zero Long constructors in the same instrumentation. Initial buffer construction and growth are separate from this steady-state observation. Models with actual Long-valued fields follow different paths.

### Removing only sizing/reservation Long math

A diagnostic replaced the generated sizing and reservation math in memory with checked Int-compatible arithmetic. Overflow validation was retained; parser and writer loops were unchanged.

| Shape, write | Original, µs/op | Checked Int sizing, µs/op |
|---|---:|---:|
| ASCII | 0.526–0.560 | 0.265–0.294 |
| BIG | 856.7–859.9 | 850.3–853.5 |

The operations are a substantial small-write cost. Their effect is small compared with the per-byte work in BIG. This distinction is supported by the diagnostic, rather than an assumption that per-object allocations do not matter.

### Remaining usage categories

| Category | Remaining Long usage | When it runs |
|---|---|---|
| Generated sizing | Maximum encoded size, fixed bounds, string length multiplied by six | Generated writes |
| Codec hints | String and Utf8Str bounds; JsonValueSize.Static and FromValue | Initialization or when a hint is evaluated |
| Handwritten sample | Maximum encoded size calculation | Sample writes |
| Reader growth | Capacity doubling and maximum capacity checks | Buffer growth |
| Reusable sink growth | Required capacity, doubling, maximum capacity checks | Buffer growth |
| Long JSON values | LongJsonCodec and Long decimal sizing | Actual Long-valued data |
| JVM vectors | Conversion of the JVM vector mask before narrowing to Int | JVM only |
| lang packed Long API | getPackedLong/setPackedLong still exist | Not used by current measured JSON paths |
| KSP's own calculations | Compile-time arithmetic in the generator | JVM during generation |

No Long arithmetic remains in the measured text loops, Int parsing, or generated packed field matching. The generated benchmark eliminates unused packed Long APIs.

KSP compile-time arithmetic itself is not JS overhead. However, emitted Kotlin literals such as `49L` become runtime Long constructions in the generated JS and need auditing separately.

## Recommended implementation work

### 1. Use checked Int sizing and reservation

Buffers and their indices are Int-sized. Generated size hints can reject overflow before multiplication and accumulation instead of calculating the bound with emulated Long objects.

For one variable field with fixed overhead, guard that its length is at most `(Int.MAX_VALUE - fixed) / 6` before calculating `fixed + length * 6`. For multiple fields, check each addition against the remaining capacity before updating the total. Preserve the current oversized-reservation rejection behavior.

The runtime already has `reserve(Int)`. Change generated calls and evaluated hints to use it where the maximum is Int-bounded. Preserve actual Long-valued JSON codecs.

Verify negative and oversized reservations, multiplication and accumulation boundaries, generated codecs with multiple strings, and behavior near Int.MAX_VALUE. The diagnostic suggests approximately a 2× small-write benefit, but a complete source implementation must be benchmarked separately.

### 2. Use native encodeInto for large JS string inputs

Keep the efficient current small-string path. Select a native `TextEncoder.encodeInto` path for large strings and write into a view of the parser's destination buffer.

A production adapter must preserve:

- Valid offset and length checks before consuming input.
- Zero-length reads returning zero, including at EOF.
- EOF returning minus one after pending data is exhausted.
- Correct advancement by UTF-16 code units, using encodeInto's returned `read` count.
- Progress when the destination has fewer bytes than the next code point requires. Native encodeInto may write zero in that case; use a scalar fallback or pending bytes rather than letting the parser treat it as EOF.
- Existing malformed-surrogate semantics. Current Charset encoding replaces an unpaired surrogate with `?`; native TextEncoder uses U+FFFD. Choose a compatible fallback or an explicit, tested API change.
- Correct treatment of destination view offsets.

The diagnostic adapter does not establish all these semantics. Its timings show that a native path is worth implementing and validating.

### 3. Separate or inline byte-store paths

Prevent StringSource's feedback from degrading the writer's store helper. Compare independent store implementations, inlining, and typed-array access using generated code and V8 traces.

Measure writing after reader-heavy setup and after writer-only setup. Preserve signed-byte behavior, offsets, and bounds checks. Check both small and large workloads.

### 4. Consider a direct JS string reader

Native encoding improves the existing byte parser's adaptation, but still encodes the document, scans bytes, and decodes String fields. The preencoded diagnostic also remained slower than kotlinx for BIG.

A string-native reader could perform token and field matching on the input string, use native quote search, return substrings for unescaped fields, and decode escapes only when needed. Generated model construction and required-field checks could be retained, but the byte-specific read protocol would need a string-backed equivalent.

This is the larger architectural change needed to compete directly with kotlinx's observed substring advantage. Its cost and benefit should be evaluated after the smaller measured fixes.

## Benchmark interpretation and follow-up checks

- Keep String-input and UTF-8-input workloads separate. The current benchmark measures String-to-model cost. A byte-input comparison should give every implementation bytes and include any conversion required by native or kotlinx.
- Keep output representation consistent. The current writers all return Strings; Koper's final UTF-8 decoding is included.
- Keep sample generation outside timed calls and verify equivalent values and output before measurement.
- Test large ASCII, large Unicode, and large escaped text separately. The current BIG workload is mostly unescaped Unicode.
- Account for shared setup and shared helpers when interpreting JIT behavior. An unchanged method can regress because another path trains a common helper.
- Separate parser-only diagnostics from complete API measurements.
- Preserve results per experiment. Cached benchmark configuration has reused a timestamped report location during this session, so rebuilding/rerunning can overwrite a previous report.
- Inspect emitted Long calls as well as Kotlin source types. Constants and comparisons can still create emulated Long objects.
- Reproduce performance changes across fresh processes and warmup orders before selecting a final optimization.

## Commands and evidence

Full comparison:

```bash
./gradlew :benchmarks:benchmarks-json:jsBenchmarkProductionExecutableJsonComparisonBenchmark
```

SWAR runtime and generator validation:

```bash
./gradlew :libs:json:json-core:jvmTest :libs:json:json-core:jsNodeTest :libs:json:json-ksp:test :libs:serde:serde-ksp:test
```

That run passed 25 JVM runtime tests, 25 JS runtime tests, one JSON generator test, and four serde generator tests. Coverage included four-digit SWAR groups, invalid byte lanes, scanner masks, overflow and refill boundaries, UTF-8 escape runs, and generated presence masks for 65 fields.

StringSource validation:

```bash
./gradlew :libs:lang:jvmTest --tests '*StringSourceSpec'
./gradlew :libs:lang:jsNodeTest
```

StringSourceSpec passed two JVM tests and two JS tests covering destination sizes 1–16, partial UTF-8 sequences, surrogate pairs, malformed surrogates, untouched surrounding bytes, zero-length reads, EOF, and invalid ranges. The unfiltered JS suite was used to verify that the tests actually ran; an earlier filtered invocation matched only the Kotest launcher.

Existing lint issues were outside these changes: platform filename rules and existing lang violations. json-ksp lint passed. A combined lint/JS-test invocation also exposed an existing Gradle production-sync/development-test dependency issue; separate test invocations passed.

Generated production modules inspected:

```text
benchmarks/benchmarks-json/build/compileSync/js/jsBenchmark/jsBenchmarkProductionExecutable/kotlin/
  koper-benchmarks-benchmarks-json.js
  koper-libs-json-json-core.js
  koper-libs-lang.js
  kotlin-kotlin-stdlib.js
  kotlinx-serialization-kotlinx-serialization-json.js
```

Installed dependency source inspected: `kotlinx-serialization-json-js-1.11.0-sources.jar`, especially common `Json.kt` and `StringJsonLexer.kt`.

Temporary diagnostic artifacts from this session:

| Artifact | Purpose |
|---|---|
| `/tmp/koper-js-diagnostic.cjs` | Stage timings, complete-read substitutions, CPU profiling |
| `/tmp/koper-js-read.cpuprofile` | BIG read sampling profile |
| `/tmp/koper-js-profile-summary.txt` | Read profile summary |
| `/tmp/koper-js-write-profile.cjs` | Writer profiling |
| `/tmp/koper-js-write.cpuprofile` | Writer sampling profile |
| `/tmp/koper-js-write-training.cjs` | Reader-training comparison |
| `/tmp/koper-js-write-isolated-setter.cjs` | Independent writer setter experiment |
| `/tmp/koper-js-setter-feedback.cjs` | V8 feedback inspection |
| `/tmp/koper-js-setter-default-machinecode.txt` | Slow setter's builtin-call evidence |
| `/tmp/koper-js-setter-native-machinecode.txt` | Fast setter's direct-store evidence |
| `/tmp/koper-js-string-layout.txt` | V8 string representation inspection |
| `/tmp/koper-js-count-long.cjs` | Generated Long constructor instrumentation |
| `/tmp/koper-js-no-size-long.cjs` | Checked sizing/reservation diagnostic |

The temporary files may disappear. The measurements, relevant machine-code differences, and methodology are recorded above so that the conclusions do not depend on their continued existence. The scripts refer to generated and mangled names from this build; they need adjustment after a rebuild that changes those names.

Example diagnostic invocations, run from the repository root with the same Node executable:

```bash
/var/home/vlad/.gradle/nodejs/node-v24.16.0-linux-x64/bin/node /tmp/koper-js-diagnostic.cjs encodeOnly
/var/home/vlad/.gradle/nodejs/node-v24.16.0-linux-x64/bin/node /tmp/koper-js-diagnostic.cjs encodeInto
/var/home/vlad/.gradle/nodejs/node-v24.16.0-linux-x64/bin/node /tmp/koper-js-diagnostic.cjs preencoded
/var/home/vlad/.gradle/nodejs/node-v24.16.0-linux-x64/bin/node /tmp/koper-js-diagnostic.cjs profile
/var/home/vlad/.gradle/nodejs/node-v24.16.0-linux-x64/bin/node /tmp/koper-js-count-long.cjs
```

The runtime substitutions and constructor instrumentation are diagnostic tools. They do not modify repository files, and their timing or allocation counts should not be presented as results of a completed production optimization.
