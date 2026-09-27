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

JMH results from 2026-09-27 in ns/op (lower is better). Generated and handwritten codecs use vectorized mode. All cases
use the same input bytes and three forks, three 1-second warmups, and five 1-second measurements per fork. Generated and
handwritten reads check trailing EOF; DSL-JSON reads use its existing entrypoint. Payload sizes are 281, 233, 289, and
41,033 bytes in the order shown below. All results used Adoptium JDK 26.

Report: `benchmarks/benchmarks-json/build/reports/benchmarks/jsonComparison/2026-09-27T22.05.52.781444081/jvm.json`.

| UTF-8 sample | Operation | Generated | DSL-JSON UTF-8 | DSL-JSON direct | Ideal handwritten |
|---|---:|---:|---:|---:|---:|
| ASCII_SMALL | Read | 99.7 | 194.3 | 192.1 | 122.2 |
| ASCII_SMALL | Write | 53.3 | 125.2 | 125.2 | 60.5 |
| UTF8_SMALL | Read | 103.2 | 178.1 | 175.9 | 125.3 |
| UTF8_SMALL | Write | 57.8 | 105.1 | 105.0 | 64.6 |
| ESCAPED_SMALL | Read | 229.1 | 407.8 | 376.1 | 250.0 |
| ESCAPED_SMALL | Write | 144.5 | 208.2 | 204.7 | 144.3 |
| UTF8_LARGE | Read | 4,649.1 | 63,239.0 | 63,458.7 | 4,788.5 |
| UTF8_LARGE | Write | 2,164.6 | 16,380.3 | 16,382.7 | 1,765.4 |

| Mixed sample (String symbol, Utf8Str text) | Operation | Generated | DSL-JSON mixed | Ideal handwritten |
|---|---:|---:|---:|---:|
| ASCII_SMALL | Read | 101.1 | 199.7 | 95.2 |
| ASCII_SMALL | Write | 52.3 | 124.6 | 62.4 |
| UTF8_SMALL | Read | 102.7 | 179.6 | 102.1 |
| UTF8_SMALL | Write | 58.3 | 103.1 | 67.1 |
| ESCAPED_SMALL | Read | 229.4 | 397.8 | 224.5 |
| ESCAPED_SMALL | Write | 151.5 | 201.9 | 144.8 |
| UTF8_LARGE | Read | 4,643.1 | 64,843.8 | 4,767.8 |
| UTF8_LARGE | Write | 2,219.4 | 16,326.6 | 1,603.9 |
