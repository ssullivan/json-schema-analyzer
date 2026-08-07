_default:
    @just --list

# Build all modules and run the full test suite
build:
    mvn clean install

# Run tests only
test:
    mvn test

# Run the CLI against a file, e.g. `just run -i example.json` or `just run < example.json`
run *args:
    #!/usr/bin/env bash
    set -euo pipefail
    jar=$(ls cli/target/json-schema-explorer-*.jar | grep -v -e sources -e javadoc -e original | head -1)
    if [ -z "$jar" ]; then
        echo "No CLI jar found — run 'just build' first" >&2
        exit 1
    fi
    java -jar "$jar" {{args}}

# Remove build output
clean:
    mvn clean

# Cut a release: bump to `version`, tag with release notes, and push — release.yml then
# builds the jar and publishes the GitHub release using the tag message as its notes.
# e.g. `just release 1.0.1 1.0.2-SNAPSHOT` opens $EDITOR for the notes, or
# `just release 1.0.1 1.0.2-SNAPSHOT notes.txt` reads them from a file.
release version next notes_file='':
    #!/usr/bin/env bash
    set -euo pipefail

    dirty_check=(git status --porcelain)
    if [ -n "{{notes_file}}" ]; then
        dirty_check+=(-- ":(exclude){{notes_file}}")
    fi
    if [ -n "$("${dirty_check[@]}")" ]; then
        echo "Working tree is not clean — commit or stash first" >&2
        exit 1
    fi

    mvn versions:set -DnewVersion={{version}} -DgenerateBackupPoms=false -q
    mvn clean install
    git add -A
    git commit -m "Release {{version}}"
    if [ -n "{{notes_file}}" ]; then
        git tag -a v{{version}} -F "{{notes_file}}"
    else
        git tag -a v{{version}}
    fi

    mvn versions:set -DnewVersion={{next}} -DgenerateBackupPoms=false -q
    mvn -q clean install -DskipTests
    git add -A
    git commit -m "Bump version to {{next}} for continued development"

    git push origin main
    git push origin v{{version}}
    echo "Pushed v{{version}} — release.yml will build and publish it: https://github.com/ssullivan/json-schema-analyzer/actions"
