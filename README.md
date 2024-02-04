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
Usage: json-analyze -i=<file>
  -i, --input-file=<file>   The JSON file to analyze
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
  "product.weight" : "float",
  "sizes" : [ {
    "size" : "integer"
  } ],
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
  "product.weight" : "float",
  "sizes" : [ {
    "size" : "integer"
  } ],
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