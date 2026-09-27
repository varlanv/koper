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
  cover that run; write unbounded fields through checked, streaming operations. The current `NativeJsonUtf8Codec`
  demonstrates the all-bounded case; a mixed case still needs to be implemented and measured.
- For strings, budget the worst-case escaped size: `2 + 6 * String.length` or `2 + 6 * Utf8Str.bytes.len` including
  quotes. Control bytes can expand to six ASCII bytes. Add fixed punctuation, field names, numeric maxima, and booleans
  to the object bound. Do the arithmetic in `Long`, then check the supported `Int`/buffer limit before converting.
  `NativeJsonUtf8Codec` uses `87L + 6L * symbol.length + 6L * text.bytes.len` for its exact shape.
- `IdealJsonWriter.reserveObject` ensures contiguous capacity before the reserved write methods. `writeRawReserved`,
  `writeLongReserved`, `writeIntReserved`, `writeStringReserved`, and `writeUtf8Reserved` then avoid repeated capacity
  checks. Their callers must prove the reservation covers every byte; these methods do not check it. The native codec
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

JMH results from one run, ns/op (lower is better). Generated and handwritten codecs use vectorized mode. All cases use
the same input bytes and one fork, three 1-second warmups, and five 1-second measurements on Adoptium JDK 26. Generated
and handwritten reads check trailing EOF; DSL-JSON reads use its existing entrypoint. Payload sizes are 281, 233, 289,
and 41,033 bytes in the order shown below.

| UTF-8 sample | Operation | Generated | DSL-JSON UTF-8 | DSL-JSON direct | Ideal handwritten |
|---|---:|---:|---:|---:|---:|
| ASCII_SMALL | Read | 142.8 | 202.9 | 201.6 | 120.3 |
| ASCII_SMALL | Write | 54.0 | 125.0 | 126.6 | 61.1 |
| UTF8_SMALL | Read | 144.0 | 178.6 | 179.3 | 124.6 |
| UTF8_SMALL | Write | 57.1 | 106.2 | 104.9 | 64.5 |
| ESCAPED_SMALL | Read | 275.5 | 409.7 | 410.4 | 247.8 |
| ESCAPED_SMALL | Write | 150.4 | 210.4 | 205.0 | 149.1 |
| UTF8_LARGE | Read | 4,940.7 | 63,084.6 | 63,309.8 | 4,752.8 |
| UTF8_LARGE | Write | 2,196.6 | 16,393.9 | 16,309.5 | 1,737.5 |

| Native sample (String symbol, Utf8Str text) | Operation | Generated | Ideal handwritten |
|---|---:|---:|---:|
| ASCII_SMALL | Read | 174.9 | 96.4 |
| ASCII_SMALL | Write | 53.6 | 60.0 |
| UTF8_SMALL | Read | 140.2 | 99.5 |
| UTF8_SMALL | Write | 59.4 | 66.2 |
| ESCAPED_SMALL | Read | 282.7 | 205.5 |
| ESCAPED_SMALL | Write | 141.3 | 138.1 |
| UTF8_LARGE | Read | 4,687.1 | 5,044.5 |
| UTF8_LARGE | Write | 2,077.5 | 1,615.6 |
