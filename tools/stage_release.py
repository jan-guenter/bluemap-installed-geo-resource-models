#!/usr/bin/env python3
"""Stage deterministic publication files and their hashes."""

from __future__ import annotations

import argparse
import hashlib
import shutil
from pathlib import Path


def property_value(path: Path, key: str) -> str:
    matches = []
    for line in path.read_text(encoding="utf-8").splitlines():
        name, separator, value = line.partition("=")
        if separator and name.strip() == key:
            matches.append(value.strip())
    if len(matches) != 1 or not matches[0]:
        raise ValueError(f"expected one non-empty {key} property")
    return matches[0]


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(64 * 1024):
            result.update(chunk)
    return result.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("destination", type=Path)
    arguments = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    properties = root / "gradle.properties"
    artifact = property_value(properties, "artifact_id")
    version = property_value(properties, "module_version")
    destination = arguments.destination.resolve()
    destination.mkdir(parents=True, exist_ok=False)

    sources = {
        root / "build" / "libs" / f"{artifact}-{version}.jar":
            destination / f"{artifact}-{version}.jar",
        root / "build" / "libs" / f"{artifact}-{version}-sources.jar":
            destination / f"{artifact}-{version}-sources.jar",
        root / "build" / "publications" / "installedGeo" / "pom-default.xml":
            destination / f"{artifact}-{version}.pom",
        root / "build" / "publications" / "installedGeo" / "module.json":
            destination / f"{artifact}-{version}.module.json",
    }
    for source, target in sources.items():
        if not source.is_file():
            raise FileNotFoundError(source)
        shutil.copyfile(source, target)

    files = sorted(sources.values(), key=lambda path: path.name)
    sums = "".join(f"{digest(path)}  {path.name}\n" for path in files)
    (destination / "SHA256SUMS").write_text(
        sums, encoding="utf-8", newline="\n"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
