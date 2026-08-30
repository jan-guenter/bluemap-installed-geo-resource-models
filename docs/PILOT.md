# Pilot contract

The initial cohort is Ars Technica, Ars Nouveau, and Ars Creo. Ars Technica
provides the generic contract-driven compiler, Ars Nouveau is an independent
static-mesh control, and Ars Creo exercises the sampled-pose extension. The
module deliberately excludes their resource admission, named model contracts,
animation parser, BlueMap emitters, routes, and fallback policy.

## Frozen parity baselines

| Consumer | Main commit with parity fixtures | Module role |
| --- | --- | --- |
| Ars Technica | `55279f50d2bb9a2a3da3dc460a361d5bac488330` | First migration and generic static compiler |
| Ars Nouveau | `62bcd904b678d1aa42144d638db22116d69350b9` | Ten-model static control |
| Ars Creo | `0bb6ef6cff2ce39314029889d893db7cc06dde27` | Non-identity pose and fallback control |

The shared synthetic geometry produces the same complete ordered-mesh
fingerprint in all three baselines. A second hierarchy fixture locks the static
and sampled Ars Creo transform paths. Exact algorithms and hashes are recorded
in `provenance/origins.json`.

## Migration gate

Migrate Ars Technica, then Ars Nouveau, then Ars Creo. Each consumer must pin
the reviewed module commit as a source submodule, compile its three production
files into the add-on, declare Gson 2.8.9 directly as `compileOnly`, and remove
the displaced local compiler/model types. A migration must then prove:

- the module gitlink, commit, source tree, and clean checkout are exact;
- the synthetic full-mesh fingerprints and consumer installed-resource tests
  still pass;
- every shared class appears exactly once and no module JAR is nested;
- the isolated exact-artifact, gallery, archive, and publication gates pass;
- consumer-specific contracts, resource admission, emitters, and fallback
  behavior remain local; and
- the combined 51-add-on ATMons 1.2.0 activation gate passes on both boots.

No consumer release or server deployment is authorized by the module release
alone.
