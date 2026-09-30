# JSON codec performance notes

The handwritten codecs in `benchmarks/benchmarks-json` are the performance reference for generated codecs.

## Serde and format boundary

- `serde-ksp` is the only KSP processor. It discovers annotated types, validates shared class shape (constructor or
  factory, fields, types, readable properties), and passes the result to configured generators. It loads generator classes
  from a Gradle-provided list once during initialization; an empty list warns and does no processing.
- `serde-ksp-model` owns the shared shape types and generator interface. KSP types are allowed in this boundary.
  `json-ksp` implements the interface without registering another processor. It owns JSON-specific validation and code
  emission. There is no intermediate schema file or second processor entrypoint. Future formats add generators to the
  configured list. Generator implementations have a public no-argument constructor.
- Generated codecs and public reading and writing APIs are protocol-specific. The JSON implementation keeps direct field
  access, JSON-specific field order, capacity planning, packed names, and specialized reading and writing. A generic
  runtime callback for every field would obstruct the all-fields-at-once reservation plan and must not replace the JSON
  fast path.
- The JSON facade handles input/output setup, flush, whole-document EOF checks, and repeated reads. It can expose lazy
  sequence operations such as `readLines` using one reusable reader. The exact framing rules for `readLines` remain open.
  Other protocols define their own operations; CSV, for example, reads lists rather than JSON-style root values.
- The JSON facade accepts an explicit generated JSON codec, so writing or reading does not require reflection or a runtime
  type lookup. It can provide configured default buffering and allow callers to supply reusable JSON readers or writers.
- JSON-specific value codecs remain useful for custom field types. A writer's maximum encoded size can be fixed,
  calculated from the value before writing, or unknown; unknown-size writers use the streaming path. The current
  `JsonSer`, `JsonDe`, and `JsonValueSize` API in `json-core` is an initial scratch implementation.

## KMP byte I/O

- Use the format-neutral `ByteSource` and `ByteSink` APIs in `lang`. `kotlinx.io` adapters may be offered without requiring
  that dependency in the shared byte I/O API.
- JSON readers and writers own their reusable internal byte buffers. Writers flush chunks to a sink and readers refill
  from a source, so streaming takes one pass without a full intermediate byte array. A caller-owned output array is not
  required; byte-array inputs and outputs remain optional in-memory conveniences.
- Platform-specific I/O adapters connect the shared byte API to files, sockets, or other platform facilities. Reusing a
  JSON reader or writer is independent of the platform source or sink type.

## Open design questions

- Who closes the source or sink when a reader or writer is reused?
- What overloads should the JSON facade provide for explicit codecs, buffering configuration, and reusable readers or
  writers?
- Should sequence reading support strict JSON Lines, whitespace-separated JSON values, or both? Define framing and error
  behavior for each supported mode.
- How does KSP find custom field codecs, and which codec wins when a custom codec targets a built-in type?
- Should generated code call built-in codec objects or direct reader and writer methods on hot paths? Benchmark both before
  choosing.

## Writing

- Plan output field order for capacity reservation, independently of constructor or declaration order. Separate fields
  with a compile-time maximum byte count (for example integer, boolean, encoded field name), fields whose maximum can be
  calculated once from the value at write time (`String`, `Utf8Str`), and fields with no known maximum (for example an
  arbitrary `Iterable`). Place the bounded fields in one contiguous run, at the start or end, so one reservation can
  cover that run; write unbounded fields through checked, streaming operations. The current `MixedJsonUtf8Codec`
  demonstrates the all-bounded case; a case with bounded and unbounded fields still needs to be implemented and measured.
- For strings, budget the worst-case escaped size: `2 + 6 * String.length` or `2 + 6 * Utf8Str.bytes.len` including
  quotes. Control bytes can expand to six ASCII bytes. Add fixed punctuation, field names, numeric maxima, and booleans
  to the object bound. Use checked `Int` arithmetic for buffer sizing: `JsonWriteProtocol.addStringSize` checks the
  remaining capacity before multiplying and accumulating each string bound. Runtime size calculation does not need `Long`.
- `IdealJsonWriter.reserveObject` ensures contiguous capacity before the reserved write methods. `writeRawReserved`,
  `writeLongReserved`, `writeIntReserved`, `writeStringReserved`, and `writeUtf8Reserved` then avoid repeated capacity
  checks. Their callers must prove the reservation covers every byte; these methods do not check it. The mixed sample codec
  takes this path only up to its 32 KiB object limit, then switches to checked, streaming writes. Preserve a large-value
  path instead of allocating an arbitrarily large temporary writer buffer.
- Emit fixed JSON fragments as packed bytes where the reference does, rather than rebuilding field names at runtime.
  Decimal integer writes use packed three-digit groups. `writeRaw` sends large byte slices directly to the output. The
  writer and output can be reset and reused.

## Reading

- Input field order is independent of write order. The reference reader dispatches common names with packed-word
  comparisons, then falls back to a field-name hash **and byte equality check**. Unknown values are skipped; a `seen`
  bitset checks required fields. Preserve the full-name check because hashes can collide.
- `IdealJsonReader` buffers input and handles values split across buffer boundaries. Its string fast path scans until
  quote, backslash, or control byte; escaped or split strings use reusable scratch storage. `readLongReserved` and
  `readIntReserved` attempt contiguous parsing and fall back when insufficient input remains. The scalar and vector JSON
  scanners are selected through `JsonSpecialScan`; JVM vector code is guarded by `VectorApi`, while JS uses scalar
  scanning.

## Reuse and measurement

- Use existing KMP utilities: `lang/bin/Bytes.kt` for byte slices and packed stores, `lang/text/Strings.kt` and
  `Charsets.kt` for UTF-8, and `json-core/PackedJsonBytes.kt` plus platform `JsonPlatform.*.kt` for JSON word loads and
  special-byte scanning. Packed field words and scan masks use primitive `Long` on JVM and primitive `Int` on JS;
  generated constants and required-field masks use `Int`. Int codecs accumulate and size values with `Int` arithmetic.
  Do not copy the JVM-only vector implementation into common code.
- For reusable performance utilities that are not unique to JSON encoding, consider putting them in `lang` module and
  testing/benchmarking separately.

## JSON benchmark baseline

JMH results from 2026-09-27 in ns/op (lower is better). Generated and handwritten codecs use vectorized mode. All cases
use the same input bytes and one fork, three 1-second warmups, and five 1-second measurements per fork. Generated and
handwritten reads check trailing EOF; DSL-JSON reads use its existing entrypoint. Payload sizes are 281, 233, 289, and
41,033 bytes in the order shown below. All results used Adoptium JDK 26.

| Commit SHA | CPU | RAM | OS |
|---|---|---|---|
| `0227108a38b8bd5f55a57c1bd1b46f8fc613440b` | Ryzen 9 9950X | 96 GB | Bazzite |

| UTF-8 sample | Operation | Generated | DSL-JSON UTF-8 | DSL-JSON direct | Ideal handwritten |
|---|---:|---:|---:|---:|---:|
| ASCII_SMALL | Read | 97.8 | 190.7 | 192.0 | 120.2 |
| ASCII_SMALL | Write | 53.8 | 125.3 | 125.0 | 61.9 |
| UTF8_SMALL | Read | 101.9 | 182.2 | 181.1 | 124.5 |
| UTF8_SMALL | Write | 57.8 | 105.8 | 104.9 | 62.1 |
| ESCAPED_SMALL | Read | 228.0 | 408.0 | 410.2 | 248.0 |
| ESCAPED_SMALL | Write | 146.9 | 206.9 | 207.5 | 150.6 |
| UTF8_LARGE | Read | 4,625.1 | 62,911.1 | 62,686.7 | 4,846.1 |
| UTF8_LARGE | Write | 2,196.2 | 16,317.3 | 16,359.4 | 1,777.7 |

| Mixed sample (String symbol, Utf8Str text) | Operation | Generated | DSL-JSON mixed | Ideal handwritten |
|---|---:|---:|---:|---:|
| ASCII_SMALL | Read | 98.9 | 199.3 | 94.9 |
| ASCII_SMALL | Write | 53.3 | 121.0 | 61.3 |
| UTF8_SMALL | Read | 100.2 | 177.5 | 97.3 |
| UTF8_SMALL | Write | 58.1 | 103.4 | 68.1 |
| ESCAPED_SMALL | Read | 230.1 | 396.1 | 224.8 |
| ESCAPED_SMALL | Write | 168.2 | 203.6 | 148.8 |
| UTF8_LARGE | Read | 4,699.7 | 63,238.6 | 4,998.2 |
| UTF8_LARGE | Write | 2,035.3 | 16,346.3 | 1,614.0 |

### Lenovo X1 Carbon Gen 13 Intel Core Ultra 7 258V Performance mode run (2026-09-27)

JMH results in ns/op (lower is better) at HEAD `6689b217907c998051f0ce289c7cf61efd49d717`. The machine has an
Intel Core Ultra 7 258V (8 cores), 32 GB RAM, and Bazzite 44. Gradle ran the benchmark on Adoptium JDK 26 (VM build
`26+35`). Run with `./gradlew :benchmarks:benchmarks-json:jvmJsonComparisonBenchmark --offline --console=plain`.
The benchmark used the same vectorized mode, payloads, one fork, three 1-second warmups, and five 1-second measurements
as the baseline above.

| UTF-8 sample | Operation | Generated | DSL-JSON UTF-8 | DSL-JSON direct | Ideal handwritten |
|---|---:|---:|---:|---:|---:|
| ASCII_SMALL | Read | 122.1 | 188.6 | 197.0 | 138.8 |
| ASCII_SMALL | Write | 48.2 | 104.3 | 101.1 | 51.4 |
| UTF8_SMALL | Read | 108.3 | 172.9 | 170.7 | 129.0 |
| UTF8_SMALL | Write | 38.3 | 77.4 | 78.7 | 45.6 |
| ESCAPED_SMALL | Read | 228.0 | 529.4 | 521.2 | 263.0 |
| ESCAPED_SMALL | Write | 252.7 | 172.9 | 173.3 | 142.9 |
| UTF8_LARGE | Read | 5,247.9 | 63,060.1 | 62,033.1 | 4,919.4 |
| UTF8_LARGE | Write | 2,349.5 | 12,074.1 | 12,077.0 | 1,897.8 |

| Mixed sample (String symbol, Utf8Str text) | Operation | Generated | DSL-JSON mixed | Ideal handwritten |
|---|---:|---:|---:|---:|
| ASCII_SMALL | Read | 116.1 | 191.1 | 109.6 |
| ASCII_SMALL | Write | 43.4 | 101.5 | 51.6 |
| UTF8_SMALL | Read | 108.8 | 180.5 | 102.0 |
| UTF8_SMALL | Write | 38.0 | 86.1 | 47.6 |
| ESCAPED_SMALL | Read | 231.9 | 513.5 | 236.6 |
| ESCAPED_SMALL | Write | 148.2 | 178.5 | 143.5 |
| UTF8_LARGE | Read | 5,428.2 | 62,539.8 | 5,233.2 |
| UTF8_LARGE | Write | 2,703.8 | 11,943.8 | 1,798.4 |

Compared with the Ryzen 9 9950X baseline, generated `UTF8_LARGE` reads took 13% longer for the UTF-8 sample and 16%
longer for the mixed sample; generated writes took 7% and 33% longer, respectively. DSL-JSON `UTF8_LARGE` writes took
about 26–27% less time on this run, while its large reads were within 2% of the baseline. The generated
`ESCAPED_SMALL` UTF-8 write was 72% slower (252.7 versus 146.9 ns/op). These are cross-machine results from different
commits, so the differences do not isolate CPU performance or a code change.
