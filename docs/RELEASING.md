# Releasing

Releases require a clean reviewed commit and a signed annotated tag named
exactly `v<module_version>`.

1. Run `clean check verifyPublication` with Gradle 9.4.0 and 9.6.1 on Java 21.
2. Rebuild with Gradle 9.6.1 from a clean state twice and compare the
   production JAR, sources JAR, POM, module metadata, and `SHA256SUMS` byte
   for byte.
3. Confirm origin, parser, pose, Checkstyle, archive, and publication gates.
4. Inspect both archives for the exact class and source rosters, notices, and
   absence of descriptors, nested JARs, resources, and host classes.
5. Merge the reviewed version commit and create the signed annotated tag there.
6. Let the release workflow validate both Gradle versions, publish a draft,
   attest the exact files, publish or safely resume the Maven package, compare
   every Maven file and release asset byte, and publish only after all checks
   match.

A module release does not authorize a consumer update or server deployment.
