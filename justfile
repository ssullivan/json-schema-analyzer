_default:
    @just --list

# Build all modules and run the full test suite
build:
    ./mvnw clean install

# Run tests only
test:
    ./mvnw test

# Run the CLI against a file, e.g. `just run -i example.json` or `just run < example.json`
run *args:
    #!/usr/bin/env bash
    set -euo pipefail
    jar=$(ls cli/target/json-schema-analyzer-*.jar | grep -v -e sources -e javadoc -e original | head -1)
    if [ -z "$jar" ]; then
        echo "No CLI jar found — run 'just build' first" >&2
        exit 1
    fi
    java -jar "$jar" {{args}}

# Measure output size against input size, e.g. `just bench` or `just bench --with-quicktype`
bench *args:
    ./bench/benchmark.py {{args}}

# Remove build output
clean:
    ./mvnw clean

# Cut a release: bump to `version`, tag with release notes, and push — release.yml then
# builds the jar and publishes the GitHub release using the tag message as its notes.
# e.g. `just release 1.0.1 1.0.2-SNAPSHOT` opens $EDITOR for the notes, or
# `just release 1.0.1 1.0.2-SNAPSHOT notes.txt` reads them from a file.
release version next notes_file='':
    #!/usr/bin/env bash
    set -euo pipefail

    exclude_notes=()
    if [ -n "{{notes_file}}" ]; then
        exclude_notes=(-- ":(exclude){{notes_file}}")
    fi
    if [ -n "$(git status --porcelain "${exclude_notes[@]}")" ]; then
        echo "Working tree is not clean — commit or stash first" >&2
        exit 1
    fi

    ./mvnw versions:set -DnewVersion={{version}} -DgenerateBackupPoms=false -q
    ./mvnw clean install
    git add -A "${exclude_notes[@]}"
    git commit -m "Release {{version}}"
    # --cleanup=verbatim keeps lines starting with '#'. Without it git treats them as
    # comments and silently strips every markdown heading out of the release notes.
    if [ -n "{{notes_file}}" ]; then
        git tag -a v{{version}} -F "{{notes_file}}" --cleanup=verbatim
    else
        git tag -a v{{version}}
    fi

    ./mvnw versions:set -DnewVersion={{next}} -DgenerateBackupPoms=false -q
    ./mvnw -q clean install -DskipTests
    git add -A "${exclude_notes[@]}"
    git commit -m "Bump version to {{next}} for continued development"

    git push origin main
    git push origin v{{version}}
    echo "Pushed v{{version}} — release.yml will build and publish it: https://github.com/ssullivan/json-schema-analyzer/actions"
