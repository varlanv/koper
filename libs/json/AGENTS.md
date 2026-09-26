# JSON codec performance notes

The hand-written codecs in `json-core/src/commonMain/kotlin/com/varlanv/koper/json/tmp` are the performance reference.
Do not change them while building the generator.

## Serde and format boundary

- `serde` owns the protocol-independent annotations, public serialization contracts, and class-shape analysis: constructor
  or factory, fields, types, and readable properties. Its KSP component is a library called by format processors, not a
  processor that must run before them.
- `json-ksp` calls the shared serde analysis during the same KSP pass and emits a JSON implementation. There is no
  generated intermediate schema file or second KSP pass. Future YAML or binary generators can reuse the same analysis
  and emit their own implementations.
- Generated format codecs should implement the public serde contracts so libraries can depend on shared serde types.
  The contract is crossed once per value; the JSON implementation keeps direct field access, JSON-specific field order,
  capacity planning, packed names, and specialized reading and writing inside that call. A generic runtime callback for
  every field would obstruct the all-fields-at-once reservation plan and must not replace the JSON fast path.
- Serde has separate public contracts for writing and reading. A generated codec handles one value; the format facade
  handles input/output setup, flush, whole-document EOF checks, and repeated reads. A JSON facade can expose lazy
  sequence operations such as `readLines` using one reusable reader. The exact framing rules for `readLines` remain open.
- Format facades accept an explicit generated codec, so writing or reading does not require reflection or a runtime type
  lookup. They can provide configured default buffering and allow callers to supply reusable format readers or writers.
  Each format defines its own top-level operations; CSV, for example, need not use JSON's root-value API.
- JSON-specific value codecs remain useful for custom field types. A writer's maximum encoded size can be fixed,
  calculated from the value before writing, or unknown; unknown-size writers use the streaming path. The current
  `JsonValueWriter`, `JsonValueReader`, and `JsonValueSize` API in `json-core` is an initial scratch implementation.
- The exact public serde method signatures and how a caller selects or replaces format implementations are deferred
  until the first generated codec. Do not assume that a format-specific reader or writer belongs in the serde contract.

## KMP byte I/O

- Put a small, format-neutral bulk byte source/sink API in `lang`. The current `JsonInput` and `JsonOutput` names in
  `json-core` are temporary and do not belong in the shared serde contract. `kotlinx.io` adapters may be offered without
  requiring that dependency in the public serde API.
- JSON readers and writers own their reusable internal byte buffers. Writers flush chunks to a sink and readers refill
  from a source, so streaming takes one pass without a full intermediate byte array. A caller-owned output array is not
  required; byte-array inputs and outputs remain optional in-memory conveniences.
- Platform-specific I/O adapters connect the shared byte API to files, sockets, or other platform facilities. Reusing a
  JSON reader or writer is independent of the platform source or sink type.

## Open design questions

- What are the exact public serde reader and writer contract signatures, including type parameters? How do the current
  JSON value codecs relate to those contracts? Settle this before generating the first codec.
- What operations should the `lang` byte source and sink expose, and who closes the source or sink when a reader or writer
  is reused?
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
