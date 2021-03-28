# Quarkus Extension Catalog

## Catalog
A catalog is a local directory (which can be cloned from a Git repository) with the following structure: 

```bash
.
├── extensions
│   └── <any_extension>.yaml
└── platforms
│   └── <any_platform,>.yaml
```

## Extensions
For maintenance purposes, each extension is declared in its own file and has the following structure:

```yaml
group-id: org.apache.myfaces.core.extensions.quarkus
artifact-id: myfaces-quarkus
``` 

The Quarkus extension MUST be released to a Maven repository. The descriptor states the Maven coordinates and the repository URL (if available).

IMPORTANT: The extension must be released using at least Quarkus 1.13.0.Final, otherwise the produced metadata won't be understood by the Registry application

## Platforms 

Platforms are a set of extensions and MUST exist as a BOM.

```yaml
group-id: "io.quarkus"
artifact-id: "quarkus-bom-quarkus-platform-descriptor"
classifier-as-version: true
```