json-schema-analyzer
---

[![Java CI with Maven](https://github.com/ssullivan/json-schema-analyzer/actions/workflows/maven.yml/badge.svg)](https://github.com/ssullivan/json-schema-analyzer/actions/workflows/maven.yml)

Understand the fields and datatypes in a JSON document without reading the whole thing.

Point it at an API response or a data export and it prints the *shape* — field names, types,
which fields are optional, how they nest — as plain output, as a JSON Schema draft-07 document,
or in a compact notation built to be pasted into an LLM prompt cheaply.

## Why this one

Most JSON shape tools target code generation or JSON Schema. This one also targets **context
windows**: the size of a description depends on the document's *shape*, not on how much data it
holds. Feed it 10,000× more records and the answer doesn't get bigger.

| Records in | Input size | `-l` output | Output ÷ input |
| --- | --- | --- | --- |
| 10 | 1.7 KB | 155 B | 9.1% |
| 1,000 | 176.6 KB | 155 B | 0.086% |
| 100,000 | **17.5 MB** | **155 B** | **0.0008%** |

Byte-identical at every size — a 17.5 MB API dump described in 155 bytes, in 390 ms. That's what
makes it usable in a prompt: cost is bounded by the schema, not the payload.

For comparison, `quicktype --lang schema` over the same inputs produces 2,299 B at 10 records and
**grows to 2,546 B** at 1,000, because it accumulates detail from the data as it goes.

Worth knowing the honest edge case: on a small, deeply irregular document, `-s` JSON Schema output
came out *larger than the raw JSON itself* (15.6 KB vs 9.4 KB), while `-l` was 252 B. Schema
formats aren't built to be compact; that's the gap this fills.

Numbers from [`bench/benchmark.py`](bench/benchmark.py) — run `just bench` to reproduce them, or
`just bench --with-quicktype` to include the comparison.

It also ships as an [Agent Skill](#agent-skills), so coding agents can use it without being told
it exists.

## Install

On macOS or Linux, via Homebrew — no Java required:

```shell
brew tap ssullivan/tap
brew install json-analyze
```

Or download a binary for your platform from the
[latest release](https://github.com/ssullivan/json-schema-analyzer/releases/latest) — no Java
required:

```shell
# macOS (Apple silicon) — adjust the platform for linux-x64, darwin-x64, or windows-x64
curl -sSLo json-analyze \
  https://github.com/ssullivan/json-schema-analyzer/releases/latest/download/json-analyze-<version>-darwin-arm64
chmod +x json-analyze
./json-analyze --help
```

Every binary ships with a `.sha256` next to it, so you can verify what you downloaded:

```shell
curl -sSLO https://github.com/ssullivan/json-schema-analyzer/releases/latest/download/json-analyze-<version>-darwin-arm64.sha256
sha256sum -c json-analyze-<version>-darwin-arm64.sha256
```

Prefer the JVM, or want the library? The runnable jar is attached to the same release and needs
a JDK 17+:

```shell
java -jar json-schema-analyzer-<version>.jar -i example.json
```

## Contents
* [Why this one](#why-this-one)
* [Install](#install)
* [Requirements](#requirements)
* [Build](#build)
* [CLI](#cli)
* [Examples](#examples)
  * [Example8: LLM-Optimized Output](#example8-llm-optimized-output)
* [Error Handling](#error-handling)
* [Using as a Library](#using-as-a-library)
* [Agent Skills](#agent-skills)
* [Versioning](#versioning)
* [Releasing](#releasing)

## Requirements
* JDK >= 17
* Maven — optional; the bundled `./mvnw` wrapper downloads a pinned version if you'd rather not
  install one

## Build

This is a two-module Maven project:
* `core` — the schema-inference library (`json-schema-analyzer-core`), no CLI dependencies.
* `cli` — the command line tool (`json-schema-analyzer`), depends on `core` and packages an
  executable fat JAR via the maven-shade-plugin.

```shell
./mvnw clean install
```

builds both modules and produces the executable CLI JAR at
`cli/target/json-schema-analyzer-<version>.jar`.

To build a native binary yourself, use a [GraalVM](https://www.graalvm.org/) JDK and activate the
`native` profile — this is what CI runs per platform to produce the released binaries:

```shell
./mvnw package -P native -DskipTests
./cli/target/json-analyze --version
```

## CLI 
```shell
Usage: json-analyze [-fhjlmsV] [-i=<file>]
Describes the fields and datatypes in a JSON document.
  -f, --detect-formats      Detect string formats (date, date-time, uuid)
                              instead of reporting every string as the generic
                              "string" type
  -h, --help                Show this help message and exit.
  -i, --input-file=<file>   The JSON file to analyze; reads from stdin if
                              omitted
  -j, --jsonl                Read the input as newline-delimited JSON (NDJSON),
                              treating each value as one sample and merging
                              them into a single shape, like -m does for a
                              JSON array
  -l, --llm                 Print the shape in a compact, punctuation-free
                              notation designed to be pasted into an LLM prompt
                              cheaply, instead of the default compact notation
  -m, --merge-samples       Treat each element of a top-level JSON array as one
                              sample and merge them into a single shape
  -s, --json-schema         Print the shape as a JSON Schema (draft-07)
                              document instead of the default compact notation
  -V, --version             Print version information and exit.
```

`-i` is optional — omit it to pipe JSON in from stdin instead:

```shell
curl -s https://api.example.com/users | java -jar json-schema-analyzer-<version>.jar
```

## Examples

### Example1: JSON Object
```shell
cat <<EOF >example.json
{
  "version": 1,
  "product": {
    "name": "Example",
    "sizes": [
      {"size" : 1 }
    ],
    "weight": 1.5,
    "enabled": true
  }
}
EOF
java -jar json-schema-analyzer-<version>.jar -i example.json
```

would produce the following output

```json
{
  "product" : {
    "enabled" : "boolean",
    "name" : "string",
    "sizes" : [ {
      "size" : "integer"
    } ],
    "weight" : "float"
  },
  "version" : "integer"
}
```

### Example2: JSON Array

```shell
cat <<EOF >example.json
[{
  "version": 1,
  "product": {
    "name": "Example",
    "sizes": [
      {"size" : 1 }
    ],
    "weight": 1.5,
    "enabled": true
  }
}]
EOF
java -jar json-schema-analyzer-<version>.jar -i example.json
```

would produce the following output

```json
[ {
  "product" : {
    "enabled" : "boolean",
    "name" : "string",
    "sizes" : [ {
      "size" : "integer"
    } ],
    "weight" : "float"
  },
  "version" : "integer"
} ]
```

### Example3: Arrays of Arrays

```shell
cat <<EOF >example.json
[
  [1],
  [0.5],
  [true, false],
  ["string"],
  []
]
EOF
java -jar json-schema-analyzer-<version>.jar -i example.json
```

would produce the following output

```json
[ [ "integer" ], [ "float" ], [ "boolean" ], [ "string" ], [ ] ]
```

### Example4: Merging Samples

By default, a top-level JSON array is treated as one document and each element's shape is
reported independently. When your array is really a collection of *sample records* — e.g. rows
exported from an API or a database — pass `-m`/`--merge-samples` to fold them into a single
combined shape instead: fields missing from some samples are marked optional (`?` suffix), and
fields whose type varies across samples become a `|`-separated union.

```shell
cat <<EOF >samples.json
[
  {"id": 1, "name": "Alice", "email": "a@example.com"},
  {"id": 2, "name": "Bob"},
  {"id": 3, "name": "Carol", "email": null},
  {"id": "4", "name": "Dave", "email": "d@example.com"}
]
EOF
java -jar json-schema-analyzer-<version>.jar -i samples.json -m
```

would produce the following output

```json
{
  "email?" : "null|string",
  "id" : "integer|string",
  "name" : "string"
}
```

Merging recurses into nested objects too — a field only present in some samples' nested objects
is marked optional *within* that nested object, not just at the top level:

```shell
cat <<EOF >nested-samples.json
[
  {"product": {"name": "A"}},
  {"product": {"name": "B", "weight": 1.5}}
]
EOF
java -jar json-schema-analyzer-<version>.jar -i nested-samples.json -m
```

```json
{
  "product" : {
    "name" : "string",
    "weight?" : "float"
  }
}
```

### Example5: NDJSON Input

Pass `-j`/`--jsonl` when the input is newline-delimited JSON (NDJSON) — one JSON value per line,
as produced by log exports or streaming APIs — rather than a single document. Each value is
treated as a sample and merged into one shape exactly the way `-m` merges elements of a JSON
array, so there's no need to wrap the lines in `[` `]` first or hold them all in memory as one
array.

```shell
cat <<EOF >samples.ndjson
{"id": 1, "name": "Alice", "email": "a@example.com"}
{"id": 2, "name": "Bob"}
{"id": "3", "name": "Carol"}
EOF
java -jar json-schema-analyzer-<version>.jar -i samples.ndjson -j
```

would produce the following output

```json
{
  "email?" : "string",
  "id" : "integer|string",
  "name" : "string"
}
```

It composes with `-s` and `-l` the same way `-m` does. `-m` itself has no effect when `-j` is
set, since NDJSON input is always merged.

### Example6: String Format Detection

Pass `-f`/`--detect-formats` to have string values checked against three known formats — ISO
8601 dates (`YYYY-MM-DD`), RFC 3339 timestamps, and canonical UUIDs — instead of every string
being reported as the generic `string` type. Off by default, so existing output is unaffected
unless you opt in.

```shell
cat <<EOF >events.json
{"id": "550e8400-e29b-41d4-a716-446655440000", "createdAt": "2023-06-01T14:22:00Z", "day": "2023-06-01", "note": "hello"}
EOF
java -jar json-schema-analyzer-<version>.jar -i events.json -f
```

would produce the following output

```json
{
  "createdAt" : "date-time",
  "day" : "date",
  "id" : "uuid",
  "note" : "string"
}
```

It composes with `-m`, `-j`, `-s`, and `-l` the same way the other flags do. When merged samples
disagree on a field's format — one row has a `date`, another has a plain string, or two rows
disagree on which format — the field falls back to the generic `string` type rather than
reporting a `date|uuid`-style union, since knowing *a* format was ambiguous is more useful than
an unreadable mix.

### Example7: JSON Schema Output

Pass `-s`/`--json-schema` to print the shape as a real [JSON Schema draft-07](https://json-schema.org/specification-links.html#draft-7)
document instead of the default compact notation — composes with `-m` the same way. Note that
JSON Schema has no separate "float" keyword, so both the `float` and merged `number` types from
the compact notation collapse to `"number"` here.

```shell
java -jar json-schema-analyzer-<version>.jar -i example.json -s
```

would produce the following output

```json
{
  "$schema" : "http://json-schema.org/draft-07/schema#",
  "type" : "object",
  "properties" : {
    "product" : {
      "type" : "object",
      "properties" : {
        "enabled" : {
          "type" : "boolean"
        },
        "name" : {
          "type" : "string"
        },
        "sizes" : {
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "size" : {
                "type" : "integer"
              }
            },
            "required" : [ "size" ]
          }
        },
        "weight" : {
          "type" : "number"
        }
      },
      "required" : [ "enabled", "name", "sizes", "weight" ]
    },
    "version" : {
      "type" : "integer"
    }
  },
  "required" : [ "product", "version" ]
}
```

### Example8: LLM-Optimized Output

Pass `-l`/`--llm` to print the shape in a compact notation with no braces, quotes, or commas —
nesting is indentation only, array-typed fields get a trailing `[]`, and optional/union fields
keep the same `?`/`|` markers as the default compact notation. It composes with `-m` the same way
`-s` does. This isn't meant to be parsed back; it's meant to describe a JSON shape to an LLM
(e.g. in a system prompt) using as few tokens as possible — see [Why this one](#why-this-one) for
measured sizes against the other modes.

```shell
java -jar json-schema-analyzer-<version>.jar -i example.json -l
```

would produce the following output

```
product:
  enabled: boolean
  name: string
  sizes[]:
    size: integer
  weight: float
version: integer
```

## Error Handling

Failures are reported as a single clean line on stderr — no stack trace — describing what was
expected and the exact line/column where things went wrong, then exit with a non-zero status:

```shell
$ java -jar json-schema-analyzer-<version>.jar -i bad.json
Error: Malformed JSON (line 2, column 1): Unexpected end-of-input within/between Object entries
```

```shell
$ java -jar json-schema-analyzer-<version>.jar -i missing.json
Error: missing.json (No such file or directory)
```

## Using as a Library

`json-schema-analyzer-core` has no CLI dependencies — depend on it directly to infer a JSON
document's shape in-process, without shelling out to the CLI:

```xml
<dependency>
    <groupId>io.github.ssullivan</groupId>
    <artifactId>json-schema-analyzer-core</artifactId>
    <version>1.3.0</version>
</dependency>
```

```java
JsonSchemaAnalyzer analyzer = new JsonSchemaAnalyzer();
JsonType schema = analyzer.parse(inputStream);

if (schema instanceof ObjectType objectType) {
    objectType.getFields().forEach((name, type) -> System.out.println(name + ": " + type));
}
```

`JsonType` is a sealed interface (`ObjectType`, `ArrayType`, `ScalarType`, `UnionType`), so callers
can pattern-match on it directly. `SchemaMerger.merge(a, b)` is available the same way for folding
multiple samples into one shape, exactly as the CLI's `-m` flag does internally. To render a shape
the way the CLI does, use `Json.write(schema)` for the compact notation or
`JsonSchemaWriter.toJsonSchema(schema)` for a draft-07 document.

For newline-delimited JSON, `analyzer.parseJsonLines(inputStream)` reads a stream of
whitespace-separated JSON values — one per line, by NDJSON convention — and merges them into a
single shape, exactly as the CLI's `-j` flag does.

To opt in to string format detection (dates, timestamps, UUIDs — off by default), construct the
analyzer with `new JsonSchemaAnalyzer(true)` instead of the no-arg constructor. `ScalarType` then
gains three additional values it can return for a string field — `DATE`, `DATE_TIME`, `UUID` — and
`JsonSchemaWriter.toJsonSchema(...)` emits a matching `"format"` keyword alongside
`"type": "string"` for them.

Collections returned by `getFields()`, `getOptionalFields()`, and `getMembers()` are unmodifiable
views. If you build shapes by hand rather than getting them from `parse()`, finish building a type
*before* adding it to another one: `equals`/`hashCode` are derived from a type's contents, and
`ArrayType`/`UnionType` hold their members in hash-based sets, so mutating an already-stored type
leaves it unfindable in the set that contains it.

`parse()` distinguishes two kinds of failure: a checked `IOException` if the stream itself can't
be read (a genuine I/O error), and an unchecked `JsonSchemaAnalysisException` if the stream reads
fine but its content isn't valid or supported JSON — malformed syntax, an empty document, and so
on. The latter's message already includes the line and column where the problem was found, so it
can be surfaced to a caller as-is without extra formatting.

## Agent Skills

This repo ships a [`json-shape`](.agents/skills/json-shape/SKILL.md) skill following the
[Agent Skills](https://agentskills.io) open standard, so an AI coding agent can infer a JSON
document's shape — using the token-cheap `-l` notation above — without knowing this tool
exists. It resolves a jar automatically: a local Maven build if you're inside a clone,
otherwise the latest published release, cached under `~/.cache/json-schema-analyzer/`.

OpenCode, Gemini CLI, and Antigravity read `.agents/skills/` natively, so the skill works as
soon as the repo is checked out. Claude Code reads `.claude/skills/` instead, which this repo
bridges with a committed symlink — recreate it with:

```shell
ln -s ../../.agents/skills/json-shape .claude/skills/json-shape
```

## Versioning

This project follows [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

* **MAJOR** — a breaking change to `json-schema-analyzer-core`'s public API (removing or
  renaming a public class/method, changing a method signature, changing what `JsonType`
  permits) or to the CLI's flags/output format.
* **MINOR** — new backward-compatible functionality: a new CLI flag, a new public method, a new
  output mode. (Note: string format detection added three new `ScalarType` constants — `DATE`,
  `DATE_TIME`, `UUID` — which is normally a MAJOR-flagged change per the bullet above, since it
  changes what `JsonType` permits. It ships as MINOR here because detection is strictly opt-in —
  off by default via the no-arg `JsonSchemaAnalyzer()` constructor and the CLI's `-f` flag both
  requiring explicit action — so no existing caller's output changes unless they opt in. Flagging
  this explicitly since it's a judgment call on an edge case this policy didn't originally
  anticipate.)
* **PATCH** — backward-compatible bug fixes, documentation, internal refactors, and dependency
  bumps that don't change public behavior.

`main` always carries a `-SNAPSHOT` suffix on the next unreleased version; see
[Releasing](#releasing) for how a version actually gets cut.

## Releasing

Cutting a release is split between a local step and CI. From a clean working tree:

```shell
just release 1.0.1 1.0.2-SNAPSHOT
```

This bumps the POMs to `1.0.1`, builds and tests locally as a sanity check, commits, and opens
your editor to write the tag's release notes (or pass a file instead:
`just release 1.0.1 1.0.2-SNAPSHOT notes.txt`). It then bumps `main` to `1.0.2-SNAPSHOT` for
continued development and pushes both. Pushing the `v1.0.1` tag triggers
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds the CLI jar from
that tag in a clean CI environment and publishes it as a GitHub release using the tag's own
message as the release notes (`gh release create --notes-from-tag`) — CI, not a local machine,
produces the artifact people actually download, but the notes are still written by hand.