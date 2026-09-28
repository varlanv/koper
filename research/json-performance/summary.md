# JSON codec performance findings

Target: generated `JsonMixedSampleJsonCodec.kt` and the runtime/generator paths it uses. Production source remains unchanged. The extended sweep contains **637 measured cases** and **15 matched repeated-fork comparisons**. CPU: Ryzen 9 7950X; JVM: Corretto 26.0.1. These are measured opportunities on this JVM, not a proof of a theoretical minimum across all hardware.

## Strongest measured opportunities

| Opportunity | Evidence | Required design |
|---|---|---|
| **Fuse string scanning/copying and write directly into the destination.** Load each vector of UTF-8 bytes once, check for quotes, backslashes and control bytes, and store clean blocks directly into an array supplied by the sink. This combines scanning with copying and removes the later copy from the writer's staging buffer into the sink; escaping still uses the existing fallback. | Large fixed write **2,074→805 ns**, saving 1,269 ns; small write 51.8→47.2 ns. Mixed 44.7 MB corpus 1,816→1,550 ns | Optional exclusive sink window; platform bulk helper; existing sink fallback |
| **Bounded streaming instead of whole-object worst-case staging.** Stop reserving a temporary buffer large enough for the entire object assuming every text byte expands to six escaped bytes. Keep staging bounded, send long spans that need no escaping directly to `ByteSink`, and process escaped output in bounded chunks. | Large write **2,046→1,407 ns**; much lower cold/growing writer allocation | Works through current ByteSink; cap staging and forward long clean spans |
| **Local input parsing plus shared Utf8 text.** Parse an existing complete input array with a local cursor, publishing the reader position once after the fast path succeeds instead of repeatedly updating reader state. For unescaped text, return an `Utf8Str` slice of that immutable array, avoiding copies through the reader/scratch buffers and into a newly allocated result array. | 1,024 distinct large inputs: **6,206→1,595 ns**, **41,096→120 B/read**. Small inputs 94.2→48.0 ns | Explicit stable immutable input ownership; owning stream fallback |
| **Ordered field continuation plus small integer helpers.** Generate direct checks for fields in the expected order, then continue through the general dispatcher on a mismatch while retaining values already read. Use smaller Int/Long helpers for ordinary contiguous numbers, reducing calls to the full parser while retaining its handling of wider numbers, overflow and split input. | Small ASCII **97.9→82.9 ns**; escaped 230.8→216.4 ns | KSP specialization with arbitrary-order/split/overflow fallback |
| **Validate skipped strings without decoding them.** When discarding an unknown field's value, check string boundaries and JSON escapes without unescaping its contents into scratch storage. Process all special-character positions from each vector mask before loading another block, avoiding both decoded-byte writes and repeated scans around escapes. | Large escaped unknown value **72,513→33,034 ns**; plain 2,212→1,745 ns | Shared runtime skip helper; preserve escape and nested-value validation |
| **Prove unknown names before hashing.** Use schema-derived prefix checks or a length proof to establish that a field name cannot match any known field, then skip its remaining bytes without computing a hash while still validating its JSON syntax. For this schema, nine literal bytes before any quote, escape or control prove a mismatch because the longest known name is eight bytes; possible matches retain full name checking. | 16 unknown 1024-byte names **11,508→828 ns** when prefix proves mismatch; extended length proof also cuts known-looking long names 11,512→903 ns | Schema-derived conservative proof, with full matching fallback |
| **Cheaper SIMD control-byte predicate.** Replace the vector test `(byte >= 0) AND (byte < 32)` with `(byte & 0xe0) == 0` when identifying JSON control bytes. The replacement recognizes the same byte values 0–31 with fewer vector operations in source; quote and backslash checks remain unchanged. | Preferred-width large write 2,037→1,910 ns in screening | `(byte & 0xe0) == 0`; checked for every byte/lane on preferred 512 and 256 widths |

Read figures above preserve the current permissive UTF-8 behavior. Future read validation remains a separate cost. Writes trust Utf8Str as requested; no validation was added to write candidates.

## Optional representations for users who can exploit reuse

- Owned prepared clean-text hints plus direct output: large write **2,083→636 ns** when prepared once and reused.
- Owned preescaped text plus direct output: small escaped write **150→32.5 ns**. Ordinary buffered preencoding paid back preparation by the second reuse in the measured small escaped case.
- For large clean text, use a clean hint: it paid back by 16 uses in the measured batch. The current preencoding factory still lost at 16 because of temporary overcapacity and copying.
- Caller-owned short-symbol cache: a repeated symbol saved 48 B and about 5 ns/read. Alternating values missed and lost time. This belongs in an explicit custom codec/context, not a singleton cache or global String interning.

These representations change ownership/reuse requirements. Prepared values own private copies; returned input slices retain their backing array. Mutable pooled input needs a lease or an owning copy.

## Other findings

- Vector configuration matters more than tiny arithmetic changes: initial large reads were about 4× faster and writes 8× faster than the scalar path. Protocol defaults currently disable vectors; activation also needs the existing JVM module/property configuration. A SWAR fallback improves scalar scanning but loses to SIMD here.
- Four adapter or codec-proxy targets increased small writes from about 54 to67 ns; two targets did not. Large writes barely changed. Preserve the flexible facade and specialize optional buffer paths. Kotlin `inline` does not automatically inline virtual codec implementations.
- Unconditional packed escape fragments saved roughly8 microseconds on dense controls but regressed quote-heavy text by 5%. The hybrid limited packing to six-byte escapes: dense controls improved 18%, but quotes still regressed 4%. This remains a workload-specific candidate. Exact output length and bounded overlapping stores are essential.
- Public VarHandle short stores remain inconclusive: the isolated escaped-write result overlaps the baseline. The combined overlay looked faster, but its additional int-load change is unused by writes; do not attribute that difference to fewer write instructions.
- Capture size-dependent properties once before reservation. This avoids repeated computed getters and ensures the same value is sized and emitted. No gain is claimed for this immutable sample, where JIT can eliminate duplicate getter loads.
- The Json facade is currently a stub. Avoid duplicate size-hint evaluation/reservation and generic Long-return boxing when completing it. Benchmark scores here cover codecs/protocols plus explicit EOF checks.

## Rejected or inconclusive approaches

- CLZ decimal sizing and paired-digit formatting did not consistently beat existing triplets.
- Removing scanner result fields alone gave no useful small gain and regressed split strings.
- Unrestricted speculative window parsing rescanned32 KiB before fallback. Guards fixed that large regression, but escaped and some4 KiB cases still lost.
- Shared ordinal dispatch added overhead for this five-field shape.
- Kotlin-inline/local-cursor output helpers showed small escaped gains with overlapping confirmation intervals and large-corpus regressions. They are not established general wins.
- Geometric capacity growth lost on the doubling-size sequence; it improved gradual growth by 15%. Bounded streaming beat both and retained much less staging memory.
- Full custom fused escaping lost on some workloads. Production already consumes all event bits in each vector mask.
- Fixed-input gains shrank on varied data. Several early short runs were still compiling during measurement; those means are not accepted as nanosecond evidence.

## Integration requirements

Keep `Json.readFrom(source, codec)` and `writeTo(sink, codec, value)` adaptable to user sources and sinks. Consumer-generated KSP code cannot use the probes' friend access to internal buffers; provide a scoped window or suitable public runtime operation. Restrict rollback-based fast parsing to safe built-in fields; custom codecs can have side effects. Numeric/string fallbacks, exact-name checks, bounds, duplicate/required-field behavior, and EOF handling remain necessary.

KMP support stays behind array/slice/offset APIs with common fallbacks and expect/actual platform helpers. JVM prototypes use public VarHandles and optional Vector API; no codec implementation uses Unsafe or private JDK access. JVM measurements do not establish JS/Native performance.

Correctness coverage includes field permutations, malformed canonical documents, escapes/surrogates, number bounds, chunk splits, unknown nesting, append offsets, reservation canaries, source mutation and switching between window/stream inputs. See [extended findings](round2/findings.md), experiment-local validation logs and [all measurements](round2/results.csv).

The final reports record both wins and rejected candidates. The [original four provisional opportunities](opportunities.md) remain preserved as the research starting point.
