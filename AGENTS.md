# Agent guide for BlueMap Installed GEO resource models

Read this file, `README.md`, `docs/ARCHITECTURE.md`, and
`provenance/origins.json` before changing production code.

## Scope

Version `0.1.0-alpha.1` contains only `InstalledGeoCompiler`,
`InstalledGeoModel`, `InstalledGeoPose`, its nested `BoneTransform`, and
the compiler's nested `Contract` in
`io.github.janguenter.bluemap.resource.installedgeo.model`.

Keep resource discovery, artifact admission, model identities, contract
constants, routes, diagnostics, activation, textures, animation sampling, and
mesh emission in consumers.

## Dependency and packaging contract

Production code uses Java 21 and Gson 2.8.9. Gson is compile-only because every
consumer already supplies it through BlueMap's runtime. Consumers must still
pin Gson 2.8.9 directly on their compile classpath instead of relying on an
incidental transitive dependency. The publication POM and Gradle metadata
intentionally declare no dependencies.

Consumers pin this repository as a Git submodule and compile its production
sources into their add-on JAR. The standalone module JAR is not a server
component. Do not add an entrypoint, descriptor, service registration,
`module-info`, nested JAR, mod metadata, resources, or runtime state.

## Origin contract

The parser and mesh math come from exact MIT sources in Ars Technica and Ars
Nouveau. Ars Creo supplies the sampled-pose behavior. The generic
`InstalledGeoPose` overload is new module code that exposes the common
transform order without moving animation sampling into this repository.

Frozen origin files and exact histories are recorded in
`provenance/origins.json`. A behavior change needs a new version, focused
differential evidence, and a consumer impact review.

## Required gates

Serialize Gradle with `/tmp/bluemap-gradle.lock` and run:

```bash
flock /tmp/bluemap-gradle.lock /path/to/gradle-9.4.0/bin/gradle \
  --no-daemon clean check verifyPublication
flock /tmp/bluemap-gradle.lock /path/to/gradle-9.6.1/bin/gradle \
  --no-daemon clean check verifyPublication
```

Before release, reproduce every publication file twice with Gradle 9.6.1 and
compare bytes. Inspect both archives and run `actionlint` after workflow
edits. A version increase and release require a reviewed pull request and a
signed annotated `v<module_version>` tag.
