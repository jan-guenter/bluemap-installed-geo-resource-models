#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail

usage() {
    echo "usage: $0 <allow-absent|require-present> <version> <release-directory>" >&2
    exit 2
}

[[ $# -eq 3 ]] || usage
mode="$1"
version="$2"
release_directory="$3"
[[ "${mode}" == "allow-absent" || "${mode}" == "require-present" ]] || usage
[[ -n "${version}" && -d "${release_directory}" ]] || usage
: "${GITHUB_ACTOR:?GITHUB_ACTOR is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

artifact="bluemap-installed-geo-resource-models"
base_url="https://maven.pkg.github.com/jan-guenter/${artifact}/io/github/janguenter/bluemap/${artifact}/${version}"
temporary_directory="$(mktemp -d)"
trap 'rm -rf "${temporary_directory}"' EXIT

remote_names=(
    "${artifact}-${version}.jar"
    "${artifact}-${version}-sources.jar"
    "${artifact}-${version}.pom"
    "${artifact}-${version}.module"
)
local_names=(
    "${artifact}-${version}.jar"
    "${artifact}-${version}-sources.jar"
    "${artifact}-${version}.pom"
    "${artifact}-${version}.module.json"
)

present=0
absent=0
for index in "${!remote_names[@]}"; do
    remote_name="${remote_names[$index]}"
    local_name="${local_names[$index]}"
    local_path="${release_directory}/${local_name}"
    download_path="${temporary_directory}/${remote_name}"
    [[ -f "${local_path}" ]] || {
        echo "missing local publication file: ${local_path}" >&2
        exit 1
    }
    status="$(curl --location --silent --show-error \
        --user "${GITHUB_ACTOR}:${GITHUB_TOKEN}" \
        --output "${download_path}" --write-out '%{http_code}' \
        "${base_url}/${remote_name}")"
    case "${status}" in
        200)
            present=$((present + 1))
            cmp "${local_path}" "${download_path}"
            ;;
        404)
            absent=$((absent + 1))
            rm -f "${download_path}"
            ;;
        *)
            echo "unexpected HTTP ${status} for ${remote_name}" >&2
            exit 1
            ;;
    esac
done

if [[ ${present} -eq ${#remote_names[@]} ]]; then
    echo present
elif [[ ${absent} -eq ${#remote_names[@]} && "${mode}" == "allow-absent" ]]; then
    echo absent
elif [[ ${absent} -eq ${#remote_names[@]} ]]; then
    echo "Maven publication is absent after publish step" >&2
    exit 1
else
    echo "Maven publication is partial: ${present} present, ${absent} absent" >&2
    exit 1
fi
