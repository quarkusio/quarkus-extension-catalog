# Quarkus Extension Catalog

This repository is the backing catalog for the [Quarkus community extension registry](https://quarkus.io/guides/extension-registry-user#registry.quarkus.io). 
Extensions added here will be included in registry.quarkus.io.

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

## Opting in to be recognized as a sponsor or contributor 

As well as the catalog information, this repository also holds information about which organizations and companies have opted-in to be shown in the Quarkiverse tooling. 
The opt-in information is in the [named-contributing-orgs-opt-in.yml](named-contributing-orgs-opt-in.yml) file. 
If a company has made a sustained contribution to an extension, the Quarkiverse tooling will auto-detect it as a sponsor. It won't identify a company as a sponsor without an opt-in. 
To opt-in, add your company's name to the `named-sponsors` list. 
If you would like your company to be shown on things like contributor graphs, but *not* ever listed as a sponsor, update the `named-contributing-orgs` list.
There is no need to update `named-contributing-orgs` if your company is listed in `named-sponsors`. 

Sponsorship can also be indicated manually, by updating an extension's [extension metadata](https://quarkus.io/guides/extension-metadata).
