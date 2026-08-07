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

# Cut a release: bump to `version`, tag, publish a GitHub release, then bump main to `next`
# e.g. `just release 1.0.1 1.0.2-SNAPSHOT`
release version next:
    #!/usr/bin/env bash
    set -euo pipefail

    if [ -n "$(git status --porcelain)" ]; then
        echo "Working tree is not clean — commit or stash first" >&2
        exit 1
    fi

    mvn versions:set -DnewVersion={{version}} -DgenerateBackupPoms=false -q
    mvn clean install
    git add -A
    git commit -m "Release {{version}}"
    git tag -a v{{version}} -m "v{{version}}"

    jar_name="json-schema-explorer-{{version}}.jar"
    cp "cli/target/$jar_name" "/tmp/$jar_name"

    mvn versions:set -DnewVersion={{next}} -DgenerateBackupPoms=false -q
    mvn -q clean install -DskipTests
    git add -A
    git commit -m "Bump version to {{next}} for continued development"

    git push origin main
    git push origin v{{version}}
    gh release create v{{version}} "/tmp/$jar_name" --title v{{version}} --generate-notes

    rm "/tmp/$jar_name"
    echo "Released v{{version}}, main is now at {{next}}"
