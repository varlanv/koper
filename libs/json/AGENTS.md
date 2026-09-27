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
  to the object bound. Do the arithmetic in `Long`, then check the supported `Int`/buffer limit before converting.
  `MixedJsonUtf8Codec` uses `87L + 6L * symbol.length + 6L * text.bytes.len` for its exact shape.
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
  special-byte scanning. Do not copy the JVM-only vector implementation into common code.
- For reusable performance utilities that are not unique to JSON encoding, consider putting them in `lang` module and
  testing/benchmarking separately.

## JSON benchmark baseline

JMH results in ns/op (lower is better). Generated and handwritten codecs use vectorized mode. All cases use the same input
bytes and one fork, three 1-second warmups, and five 1-second measurements. Generated and handwritten reads check
trailing EOF; DSL-JSON reads use its existing entrypoint. Payload sizes are 281, 233, 289, and 41,033 bytes in the order
shown below. The UTF-8 sample results used Adoptium JDK 26; the mixed sample results used Amazon JDK 26.0.1.

UTF-8 sample report: `benchmarks/benchmarks-json/build/reports/benchmarks/jsonComparison/packed-field-read-2026-09-27/jvm.json`.
Mixed sample reports: `benchmarks/benchmarks-json/build/reports/benchmarks/jsonComparison/dsl-mixed-2026-09-27.json`
and `benchmarks/benchmarks-json/build/reports/benchmarks/jsonComparison/mixed-generated-ideal-2026-09-27.json`.

| UTF-8 sample | Operation | Generated | DSL-JSON UTF-8 | DSL-JSON direct | Ideal handwritten |
|---|---:|---:|---:|---:|---:|
| ASCII_SMALL | Read | 107.0 | 193.5 | 204.7 | 120.5 |
| ASCII_SMALL | Write | 53.2 | 125.2 | 126.8 | 60.6 |
| UTF8_SMALL | Read | 112.3 | 183.0 | 173.9 | 126.3 |
| UTF8_SMALL | Write | 56.6 | 107.1 | 107.9 | 64.0 |
| ESCAPED_SMALL | Read | 242.9 | 413.1 | 409.4 | 246.2 |
| ESCAPED_SMALL | Write | 141.5 | 211.1 | 212.0 | 144.1 |
| UTF8_LARGE | Read | 4,794.7 | 63,024.7 | 63,228.0 | 4,786.8 |
| UTF8_LARGE | Write | 2,178.5 | 16,465.4 | 16,328.6 | 1,772.6 |

| Mixed sample (String symbol, Utf8Str text) | Operation | Generated | DSL-JSON mixed | Ideal handwritten |
|---|---:|---:|---:|---:|
| ASCII_SMALL | Read | 107.4 | 198.8 | 94.0 |
| ASCII_SMALL | Write | 52.8 | 125.8 | 59.7 |
| UTF8_SMALL | Read | 109.7 | 177.2 | 99.2 |
| UTF8_SMALL | Write | 58.3 | 102.4 | 65.5 |
| ESCAPED_SMALL | Read | 249.3 | 393.5 | 223.0 |
| ESCAPED_SMALL | Write | 145.2 | 206.7 | 136.8 |
| UTF8_LARGE | Read | 4,612.4 | 63,435.1 | 4,758.8 |
| UTF8_LARGE | Write | 2,029.4 | 16,322.3 | 1,604.2 |
