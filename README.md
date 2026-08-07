json-schema-explorer
---

A small command line utility to understand the fields, and datatypes in a JSON document.

## Requirements
* JDK >= 17
* Maven

## Build

This project creates an executable fat JAR file using the spring-boot-maven-plugin. The JAR will 
contain the dependencies necessary for execution.

```shell
mvn clean install
```

## CLI 
```shell
Usage: json-analyze [-m] -i=<file>
  -i, --input-file=<file>   The JSON file to analyze
  -m, --merge-samples       Treat each element of a top-level JSON array as one
                             sample and merge them into a single shape
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
  "product.enabled" : "boolean",
  "product.name" : "string",
  "product.sizes" : [ {
    "size" : "integer"
  } ],
  "product.weight" : "float",
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
  "product.enabled" : "boolean",
  "product.name" : "string",
  "product.sizes" : [ {
    "size" : "integer"
  } ],
  "product.weight" : "float",
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