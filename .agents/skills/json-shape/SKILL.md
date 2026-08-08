---
name: json-shape
description: Summarizes the fields, types, optionality, and nesting of a JSON document by running the json-schema-analyzer CLI, using a compact notation that costs a fraction of the tokens of the raw JSON. Use before writing code against an unfamiliar JSON payload — an API response, a config file, a sample data export — or whenever you need a document's structure without spending context on its contents. Can also emit a real JSON Schema (draft-07) document when one is explicitly needed.
license: Apache-2.0
compatibility: Requires bash and a JRE 17+ on PATH. Uses a local Maven build (cli/target/json-schema-analyzer-*.jar) when run inside a clone of this repo; otherwise requires curl and network access to fetch a released jar from GitHub. No native Windows cmd/PowerShell support — use WSL or Git Bash.
---

# JSON shape analysis

Infers the structure of a JSON document — what fields exist, what type each holds, which are
optional, how they nest — and prints it far more compactly than the document itself. Useful
when you need to *write code against* JSON whose shape you don't already know, without
reading the whole payload into context.

## When to use this skill

- You're about to parse an API response, config file, or data export and don't know its shape.
- A JSON file is too large or repetitive to paste, but you need its structure.
- You have an array of sample records and want one merged shape showing which fields are
  optional and which vary in type.
- You need a real JSON Schema (draft-07) document to hand to a validator or code generator.

## Usage

```shell
bash scripts/analyze.sh [flags]
```

Invoke it with an explicit `bash` prefix rather than relying on the executable bit. Input
comes from `-i <file>` or from stdin if `-i` is omitted, so piping works:

```shell
curl -s https://api.example.com/users | bash scripts/analyze.sh -l -m
```

The wrapper forwards every argument straight through to the CLI and adds no flags of its
own — choose them using the guidance below.

## Choosing flags

| Flag | When |
| --- | --- |
| `-l`, `--llm` | **Default choice.** Compact, punctuation-free notation — cheapest to read. |
| `-m`, `--merge-samples` | The input is a top-level *array of sample records* rather than one document. Folds them into a single shape: fields missing from some samples get `?`, fields whose type varies become a `\|` union. |
| `-s`, `--json-schema` | Only when a real JSON Schema document is explicitly wanted. Much more verbose than `-l`. |
| *(none)* | The default JSON-shaped compact notation. Prefer `-l` unless you specifically want valid JSON out. |

`-s` and `-l` are mutually exclusive; passing both fails with a clear error.

## Examples

Single document:

```shell
bash scripts/analyze.sh -l -i example.json
```

```
product:
  enabled: boolean
  name: string
  sizes[]:
    size: integer
  weight: float
version: integer
```

Nesting is indentation only. A field holding an array gets `[]` after its name; an optional
field gets `?`; a field with more than one observed type joins them with `|`.

Merging samples piped in from an API:

```shell
curl -s https://api.example.com/users | bash scripts/analyze.sh -l -m
```

```
email?: null|string
id: integer|string
name: string
```

Here `email` was absent from at least one record, and `id` appeared as both an integer and a
string — the kind of detail worth knowing *before* writing the parsing code.

The `-l` notation is specified in full in the project README's "Example6: LLM-Optimized
Output" section.

## Requirements and troubleshooting

Needs `java` (17+) on PATH, plus `curl` when no local build is available.

The script prefers a locally-built jar, searching upward from the current directory for
`cli/target/json-schema-analyzer-*.jar`. Outside a clone of the repo it downloads the latest
released jar to `~/.cache/json-schema-analyzer/` (override with `JSON_SCHEMA_ANALYZER_CACHE`)
and reuses it on later runs.

If you see `Unknown option: '-l'` (or `-m`/`-s`), the jar being used predates that flag —
almost always the downloaded release rather than a local build. Building the repo locally
(`mvn clean install`) makes the script prefer that fresh jar instead.
