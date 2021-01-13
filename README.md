# Quarkus Extension Catalog

## Catalog
A catalog is a local directory (which can be cloned from a Git repository) with the following structure: 

```bash
.
├── extensions
│   └── <any_extension>.json
└── platforms.json
```

## Extensions
For maintenance purposes, each extension is declared in its own file and has the following structure:

```json
{
  "group-id": "org.apache.myfaces.core.extensions.quarkus",
  "artifact-id": "myfaces-quarkus"
}
``` 

The Quarkus extension MUST be released to a Maven repository. The descriptor states the Maven coordinates and the repository URL (if available).

## Platforms 

Platforms are a set of extensions and MUST exist as a BOM.

```json
[
  {
    "group-id": "io.quarkus",
    "artifact-id": "quarkus-universe-bom"
  },
  {
    "group-id": "io.quarkus",
    "artifact-id": "quarkus-bom",
    "artifact-id-json": "quarkus-bom-descriptor-json"
  }
]
```