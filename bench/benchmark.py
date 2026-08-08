#!/usr/bin/env python3
"""Measure how json-analyze's output size behaves as input size grows.

The interesting property is that the *shape* of a document is roughly constant in size no
matter how much data the document holds, so describing a 10 MB API dump costs about what
describing a 1 KB one does. This prints the numbers so that claim can be checked rather than
asserted.

Usage:
    ./bench/benchmark.py                     # jar from cli/target, default sizes
    ./bench/benchmark.py --binary ./json-analyze
    ./bench/benchmark.py --sizes 10 1000     # quicker run
    ./bench/benchmark.py --with-quicktype    # also size quicktype's JSON Schema output
    ./bench/benchmark.py --out bench/RESULTS.md
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import platform
import random
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SEED = 1234
TIMING_RUNS = 3

# (label, flags). The empty flag list is the default compact notation.
MODES = [
    ("-l (llm)", ["-l"]),
    ("default", []),
    ("-s (json schema)", ["-s"]),
]


def find_jar() -> Path | None:
    """Mirrors how the justfile `run` recipe and the json-shape skill locate the CLI."""
    candidates = [
        p for p in glob.glob(str(REPO_ROOT / "cli/target/json-schema-analyzer-*.jar"))
        if not any(x in p for x in ("sources", "javadoc", "original"))
    ]
    return Path(sorted(candidates)[-1]) if candidates else None


def build_command(args) -> list[str]:
    if args.binary:
        binary = Path(args.binary).resolve()
        if not binary.exists():
            sys.exit(f"error: no such binary: {binary}")
        return [str(binary)]
    jar = find_jar()
    if jar is None:
        sys.exit("error: no CLI jar found — run `just build` first, or pass --binary")
    return ["java", "-jar", str(jar)]


def make_record(rng: random.Random, index: int) -> dict:
    """One sample record.

    Deliberately not uniform: `email` is absent from roughly a third of records and `id`
    alternates between int and string, so merging has real optionality and unions to infer
    rather than the easiest possible case.
    """
    record = {
        "id": index if index % 2 == 0 else str(index),
        "name": f"user-{rng.randrange(100000)}",
        "active": rng.choice([True, False]),
        "score": round(rng.uniform(0, 100), 3),
        "tags": [f"tag-{rng.randrange(50)}" for _ in range(rng.randrange(0, 4))],
        "profile": {
            "city": rng.choice(["Portland", "Austin", "Boston", "Denver"]),
            "zip": rng.randrange(10000, 99999),
            "verified": rng.choice([True, False, None]),
        },
    }
    if rng.random() > 0.3:
        record["email"] = f"user{index}@example.com"
    return record


def write_samples(path: Path, count: int) -> None:
    rng = random.Random(SEED)
    path.write_text(json.dumps([make_record(rng, i) for i in range(count)]))


def write_nested(path: Path) -> None:
    """A single deeply-nested, heterogeneous document.

    The array cases are uniform records, which is the shape that flatters this tool most;
    this one is here so the table isn't only that case.
    """
    rng = random.Random(SEED)
    doc = {
        "meta": {"version": 3, "generated": "2026-01-01T00:00:00Z", "flags": [True, False]},
        "config": {
            "limits": {"soft": 10, "hard": 100.5, "unlimited": None},
            "targets": [{"host": f"h{i}.example.com", "port": 8000 + i} for i in range(5)],
        },
        "mixed": [1, "two", 3.0, True, None, {"nested": {"deep": [1, 2, 3]}}],
        "records": [make_record(rng, i) for i in range(50)],
    }
    path.write_text(json.dumps(doc))


def run(cmd: list[str], input_path: Path) -> tuple[str, float]:
    """Runs the CLI once, returning stdout and the median wall-clock of TIMING_RUNS runs."""
    timings = []
    output = ""
    for _ in range(TIMING_RUNS):
        start = time.perf_counter()
        proc = subprocess.run(cmd + ["-i", str(input_path)], capture_output=True, text=True)
        timings.append((time.perf_counter() - start) * 1000)
        if proc.returncode != 0:
            sys.exit(f"error: {' '.join(cmd)} failed:\n{proc.stderr or proc.stdout}")
        output = proc.stdout
    return output, statistics.median(timings)


def run_quicktype(input_path: Path) -> int | None:
    """Size of quicktype's JSON Schema for the same input, for an apples-to-apples number."""
    if shutil.which("npx") is None:
        print("  (skipping quicktype: npx not on PATH)", file=sys.stderr)
        return None
    proc = subprocess.run(
        ["npx", "-y", "quicktype", "--lang", "schema", "--src", str(input_path)],
        capture_output=True, text=True,
    )
    if proc.returncode != 0:
        print(f"  (quicktype failed: {(proc.stderr or '').strip()[:200]})", file=sys.stderr)
        return None
    return len(proc.stdout.encode())


def token_counter():
    """Real token counts if tiktoken is installed; otherwise None.

    No bytes/4 estimate — a made-up number dressed as a measurement is worse than no number.
    """
    try:
        import tiktoken
    except ImportError:
        return None
    encoding = tiktoken.get_encoding("cl100k_base")
    return lambda text: len(encoding.encode(text))


def human(num_bytes: int) -> str:
    for unit in ("B", "KB", "MB"):
        if num_bytes < 1024 or unit == "MB":
            return f"{num_bytes:,.0f} {unit}" if unit == "B" else f"{num_bytes:,.1f} {unit}"
        num_bytes /= 1024
    return f"{num_bytes} B"


def environment(cmd: list[str]) -> list[str]:
    version = subprocess.run(cmd + ["--version"], capture_output=True, text=True).stdout.strip()
    java = subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()
    return [
        f"- tool: `{version or 'unknown'}`",
        f"- runner: `{' '.join(cmd[:-1]) if cmd[0] == 'java' else 'native binary'}`",
        f"- java: `{java[0].strip() if java else 'n/a'}`",
        f"- platform: `{platform.system()} {platform.machine()}`, python {platform.python_version()}",
    ]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--binary", help="native binary to benchmark instead of the jar")
    parser.add_argument("--sizes", type=int, nargs="+", default=[10, 1000, 100000],
                        help="record counts to generate (default: 10 1000 100000)")
    parser.add_argument("--with-quicktype", action="store_true",
                        help="also measure quicktype's JSON Schema output (slow; downloads npx pkg)")
    parser.add_argument("--out", help="also write the report to this file")
    args = parser.parse_args()

    cmd = build_command(args)
    count_tokens = token_counter()
    lines: list[str] = []

    def emit(text: str = "") -> None:
        print(text)
        lines.append(text)

    emit("# json-analyze benchmarks")
    emit()
    for line in environment(cmd):
        emit(line)
    emit(f"- generated: seeded (`random.Random({SEED})`), so byte counts are reproducible")
    if count_tokens is None:
        emit("- tokens: **not measured** (`pip install tiktoken` to enable)")
    emit()

    with tempfile.TemporaryDirectory() as tmp:
        tmpdir = Path(tmp)

        emit("## Output size as input grows")
        emit()
        emit("Arrays of sample records through `-m`. The point is the last column: input grows,")
        emit("output does not.")
        emit()
        header = "| records | input | mode | output | output/input |"
        sep = "| --- | --- | --- | --- | --- |"
        if count_tokens:
            header = header[:-1] + " tokens |"
            sep = sep[:-1] + " --- |"
        emit(header)
        emit(sep)

        llm_outputs = {}
        for count in args.sizes:
            path = tmpdir / f"samples-{count}.json"
            write_samples(path, count)
            input_bytes = path.stat().st_size
            for label, flags in MODES:
                output, _ = run(cmd + ["-m"] + flags, path)
                out_bytes = len(output.encode())
                if label.startswith("-l"):
                    llm_outputs[count] = output
                row = (f"| {count:,} | {human(input_bytes)} | `{label}` | {out_bytes:,} B "
                       f"| {out_bytes / input_bytes:.4%} |")
                if count_tokens:
                    row = row[:-1] + f" {count_tokens(output):,} |"
                emit(row)
        emit()

        emit("## Time to analyze")
        emit()
        emit(f"Median of {TIMING_RUNS} runs, `-l -m`, including process startup.")
        emit()
        emit("| records | input | median |")
        emit("| --- | --- | --- |")
        for count in args.sizes:
            path = tmpdir / f"samples-{count}.json"
            _, elapsed = run(cmd + ["-m", "-l"], path)
            emit(f"| {count:,} | {human(path.stat().st_size)} | {elapsed:.0f} ms |")
        emit()

        emit("## Nested, heterogeneous document")
        emit()
        emit("Uniform records are the case that flatters this tool most; this one is messier.")
        emit()
        emit("| mode | output |")
        emit("| --- | --- |")
        nested = tmpdir / "nested.json"
        write_nested(nested)
        for label, flags in MODES:
            output, _ = run(cmd + flags, nested)
            emit(f"| `{label}` | {len(output.encode()):,} B |")
        emit(f"| *(raw input)* | {nested.stat().st_size:,} B |")
        emit()

        if args.with_quicktype:
            emit("## vs quicktype")
            emit()
            emit("`quicktype --lang schema` over the same inputs.")
            emit()
            emit("| records | quicktype schema | json-analyze `-l` |")
            emit("| --- | --- | --- |")
            for count in args.sizes:
                path = tmpdir / f"samples-{count}.json"
                qt = run_quicktype(path)
                ours = len(llm_outputs[count].encode())
                emit(f"| {count:,} | {f'{qt:,} B' if qt else 'n/a'} | {ours:,} B |")
            emit()

        # Same generator and schema at every size, so the shape must be identical. If it is
        # not, the numbers above are measuring a moving target rather than the claim.
        shapes = set(llm_outputs.values())
        emit("## Consistency check")
        emit()
        if len(shapes) == 1:
            emit(f"`-l` output was byte-identical across all {len(llm_outputs)} input sizes.")
        else:
            emit(f"**Output differed across sizes ({len(shapes)} distinct results)** — the sizes")
            emit("above are not comparable. Inspect before quoting them.")
        emit()

    if args.out:
        Path(args.out).write_text("\n".join(lines) + "\n")
        print(f"\nwrote {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
