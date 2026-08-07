json-schema-explorer
---

A small command line utility to understand the fields, and datatypes in a JSON document.

## Requirements
* JDK >= 17
* Maven

## Build

This is a two-module Maven project:
* `core` — the schema-inference library (`json-schema-explorer-core`), no CLI dependencies.
* `cli` — the command line tool (`json-schema-explorer`), depends on `core` and packages an
  executable fat JAR via the maven-shade-plugin.

```shell
mvn clean install
```

builds both modules and produces the executable CLI JAR at
`cli/target/json-schema-explorer-<version>.jar`.

## CLI 
```shell
Usage: json-analyze [-ms] -i=<file>
  -i, --input-file=<file>   The JSON file to analyze
  -m, --merge-samples       Treat each element of a top-level JSON array as one
                              sample and merge them into a single shape
  -s, --json-schema         Print the shape as a JSON Schema (draft-07)
                              document instead of the default compact notation
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
java -jar json-schema-explorer-<version>.jar -i example.json
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
java -jar json-schema-explorer-<version>.jar -i example.json
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
java -jar json-schema-explorer-<version>.jar -i example.json
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
java -jar json-schema-explorer-<version>.jar -i samples.json -m
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
java -jar json-schema-explorer-<version>.jar -i nested-samples.json -m
```

```json
{
  "product" : {
    "name" : "string",
    "weight?" : "float"
  }
}
```

### Example5: JSON Schema Output

Pass `-s`/`--json-schema` to print the shape as a real [JSON Schema draft-07](https://json-schema.org/specification-links.html#draft-7)
document instead of the default compact notation — composes with `-m` the same way. Note that
JSON Schema has no separate "float" keyword, so both the `float` and merged `number` types from
the compact notation collapse to `"number"` here.

```shell
java -jar json-schema-explorer-<version>.jar -i example.json -s
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

## Using as a Library

`json-schema-explorer-core` has no CLI dependencies — depend on it directly to infer a JSON
document's shape in-process, without shelling out to the CLI:

```xml
<dependency>
    <groupId>com.github.ssullivan</groupId>
    <artifactId>json-schema-explorer-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
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
multiple samples into one shape, exactly as the CLI's `-m` flag does internally.