# BlueMap Installed GEO resource models

This Java 21 source module holds the bounded Bedrock GEO parser and mesh model
shared by several BlueMap add-ons. Version `0.1.0-alpha.1` provides:

- `InstalledGeoCompiler` for exact `1.12.0` geometry parsing, validation,
  hierarchy transforms, mapped-face UVs, and ordered quad generation;
- `InstalledGeoModel` for immutable vertices, quads, normals, and vector math;
- `InstalledGeoPose` for optional per-bone rotation and translation samples;
  and
- `InstalledGeoCompiler.Contract` for consumer-owned bone, cube, and face
  counts.

The module contains no mod geometry, textures, model names, resource paths, or
runtime registration.

## Consumer model

Pin this repository at an exact commit as a Git submodule, then compile
`src/main/java` into the consumer:

```groovy
sourceSets {
    main.java.srcDir 'modules/bluemap-installed-geo-resource-models/src/main/java'
}
```

Do not install the standalone module JAR beside BlueMap and do not nest it in
an add-on. Each consumer owns its accepted artifact identity, resource paths,
contracts, poses, fallback policy, and final mesh emission.

## Dependencies

Production source uses Gson 2.8.9 and the Java 21 standard library. Gson stays
compile-only in this review module and is not listed in publication metadata.
There is no BlueMap, Minecraft, NeoForge, or mod dependency.

## Build

Use Java 21 with Gradle 9.4.0 or 9.6.1:

```bash
gradle --no-daemon clean check verifyPublication
```

The gate checks every parser budget, malformed schema and hierarchy failures,
identity and sampled poses, exact raw-bit mesh output, source origins,
Checkstyle, archive contents, and dependency-free publication metadata.

## Licensing and provenance

The implementation is independently authored MIT code extracted from the
three MIT BlueMap add-ons named in `provenance/origins.json`. Frozen source
oracles are tests and release evidence. No Ars Nouveau, Ars Technica, Ars
Creo, GeckoLib, BlueMap, or Minecraft asset or class is distributed.
