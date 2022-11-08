# Quarkus Extension Catalog

## Catalog
A catalog is a local directory (which can be cloned from a Git repository) with the following structure: 

```bash
.
├── extensions
│   └── <any_extension>.yaml
└── platforms
│   └── <any_platform>.yaml
```

## Extensions
For maintenance purposes, each extension is declared in its own file and has the following structure:

```yaml
group-id: org.apache.myfaces.core.extensions.quarkus
artifact-id: myfaces-quarkus
``` 

The Quarkus extension MUST be released to a Maven repository. The descriptor states the Maven coordinates and the repository URL (if available).

IMPORTANT: The extension must be released using at least Quarkus 1.13.0.Final, otherwise the produced metadata won't be understood by the Registry application

### Compatibility with older Quarkus Core versions

Having an extension built against Quarkus Core version X.Y, it is assumed that the extension release is compatible with any requested Quarkus version >= X.Y and less than (X+1).0. That is, minor Quarkus releases are assumed to be backwards compatible for extensions, but major ones are not.
If you want to mark an extension release compatible with an older Quarkus core version, or across major version boundaries, add a `compatible-with-quarkus-core` attribute inside the version, as in the following example:

```yaml
group-id: "io.quarkiverse.arthas"
artifact-id: "quarkus-arthas"
versions:
  - "0.1":
      compatible-with-quarkus-core:
        - "2.8.3.Final"

```

## Platforms 

Platforms are a set of extensions and MUST exist as a BOM.

```yaml
group-id: "io.quarkus"
artifact-id: "quarkus-bom-quarkus-platform-descriptor"
classifier-as-version: true
```
