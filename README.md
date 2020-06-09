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

The Quarkus extension MUST be released to a Maven repository. The descriptor states the GAV - the versions are always resolved to the latest version using the Maven Resolver API.

## Platforms 

Platforms are a set of extensions and MUST exist as a BOM. Since the order is important, it is declared as a single JSON (ordered by priority - the preferred BOMs in the top)

```json
[
  {
    "group-id": "io.quarkus",
    "artifact-id": "quarkus-universe-bom",
    "releases": [
      {
        "version": "1.4.2.Final"
      },      
      {
        "version": "1.3.2.Final"
      }
    ]    
  },
  {
    "group-id": "io.quarkus",
    "artifact-id": "quarkus-bom",
    "artifact-id-json": "quarkus-bom-descriptor-json",
    "releases": [
      {
        "version": "1.5.0.Final"
      },      
      {
        "version": "1.4.2.Final"
      }
    ]
    
  }
]
```

## App

Generates a Docker image to serve the produced `registry.json`
