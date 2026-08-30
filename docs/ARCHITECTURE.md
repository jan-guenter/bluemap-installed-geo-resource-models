# Architecture

## Boundary

`InstalledGeoCompiler` accepts bytes, a consumer-owned exact roster contract,
and an optional pose. It parses one Bedrock `1.12.0` geometry, rejects
unsupported schema features and over-budget input, then emits immutable quads
in source order.

`InstalledGeoPose` stores already sampled transforms. The compiler applies
cube rotation first. For each bone from child to root, it adds sampled rotation
to the static bone rotation, rotates about the static pivot, then adds sampled
translation. This is the Ars Creo order. Poses that name a missing bone fail
before mesh generation.

## Packaging

Consumers compile the three source files into their own add-on JAR. The
standalone JAR, sources JAR, POM, and Gradle module metadata are review files,
not server dependencies. They contain no descriptor, entrypoint, service,
mod metadata, nested JAR, model, texture, JSON resource, or host class.

## Deliberate exclusions

The module does not own resource catalogs, exact artifact admission, mod model
names, expected roster constants, installed-resource lookup, animation
parsing, sample timing, render materials, BlueMap mesh emission, activation,
diagnostics, or fallback behavior.

## Verification

The test suite generates its own Bedrock GEO JSON. It covers every numeric,
string, count, depth, and byte limit; malformed schema and UV input; hierarchy
failures; identity and sampled poses; unknown pose bones; and one complete
ordered mesh fingerprint built from raw double and float bits.

The origin gate hashes exact frozen Ars Technica, Ars Nouveau, and Ars Creo
sources and the three production files. No third-party model or texture is
needed to validate the module.
