#!/bin/bash
# Release gate: are the precompiled natives really built for the version being released? (B-02)
#
#   check-natives-fresh.sh [release-version]
#
# 2.10.3 was released with the 2.10.2 binaries, byte for byte (#205). Each check below would have
# stopped it:
#
#  1. SerialNativeInterface.libVersion equals the release version.
#  2. Every native embeds the release version (CMake writes it into version.h, and
#     getNativeLibraryVersion() returns it), so a library that was not rebuilt still says the
#     old version.
#  3. Every native differs from the previous release. Follows from 2, because the embedded
#     version changes, but it is cheap and also prints the hash table for the release notes.
#  4. The natives were built from the current native sources: the last commit that touched
#     them is a "Precompiled natives @<rev>" commit from cross-compile.yml, and nothing under
#     src/main/cpp, toolchain/ or CMakeLists.txt changed after <rev>.
#
# Needs the full git history and tags (actions/checkout with fetch-depth: 0).
# The release version defaults to the pom version without "-SNAPSHOT".
# A Markdown hash table is appended to $GITHUB_STEP_SUMMARY when set.
set -uo pipefail

NATIVES=src/main/resources-precompiled/natives
NATIVE_SOURCES=(src/main/cpp toolchain CMakeLists.txt)

version=${1:-}
if [ -z "$version" ]; then
    version=$(sed -n '0,/<version>/s:.*<version>\(.*\)</version>.*:\1:p' pom.xml)
    version=${version%-SNAPSHOT}
fi
echo "Checking natives for release ${version}"

failures=0
fail() {
    echo "::error::$*"
    failures=$((failures + 1))
}

# 1. libVersion
lib_version=$(sed -n 's/.*private static final String libVersion = "\(.*\)";.*/\1/p' src/main/java/jssc/SerialNativeInterface.java)
if [ "$lib_version" != "$version" ]; then
    fail "SerialNativeInterface.libVersion is ${lib_version}, expected ${version}. Run cross-compile.yml on the release branch and merge its -precompiled-natives branch first."
fi

# 2. embedded version; 3. hashes against the previous release
previous=$(git describe --tags --abbrev=0 --match 'v[0-9]*' HEAD 2>/dev/null)
if [ "$previous" = "v${version}" ]; then
    previous=$(git describe --tags --abbrev=0 --match 'v[0-9]*' "HEAD^" 2>/dev/null)
fi
echo "Previous release: ${previous:-none found}"

version_regex="(^|[^0-9.])${version//./\\.}(\$|[^0-9.])"
table="| Native | ${previous:-previous} | ${version} | |"$'\n'"|---|---|---|---|"$'\n'
for file in $(cd "$NATIVES" && find . -type f -name '*jssc*' | sed 's:^\./::' | sort); do
    if ! LC_ALL=C grep -q -a -E "$version_regex" "$NATIVES/$file"; then
        fail "${file} does not contain version ${version}: it was not rebuilt for this release"
    fi
    new_hash=$(sha256sum < "$NATIVES/$file" | cut -c1-16)
    old_hash=
    if [ -n "$previous" ]; then
        old_hash=$(git show "${previous}:${NATIVES}/${file}" 2>/dev/null | sha256sum | cut -c1-16)
        git cat-file -e "${previous}:${NATIVES}/${file}" 2>/dev/null || old_hash=
    fi
    if [ -z "$old_hash" ]; then
        note="new"
    elif [ "$old_hash" = "$new_hash" ]; then
        note="**unchanged**"
        fail "${file} is byte-identical to ${previous} (#205)"
    else
        note="rebuilt"
    fi
    echo "  ${file}: ${old_hash:--} -> ${new_hash} (${note//\*/})"
    table+="| \`${file}\` | \`${old_hash:--}\` | \`${new_hash}\` | ${note} |"$'\n'
done
if [ -n "$previous" ]; then
    for file in $(git ls-tree -r --name-only "$previous" -- "$NATIVES" | sed "s:^${NATIVES}/::"); do
        [ -e "$NATIVES/$file" ] || echo "::warning::${file} was in ${previous} but is no longer shipped"
    done
fi
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '### Natives for %s\n\n%s\n' "$version" "$table" >> "$GITHUB_STEP_SUMMARY"
fi

# 4. provenance
natives_commit=$(git log -1 --format=%H -- "$NATIVES")
subject=$(git log -1 --format=%s "$natives_commit")
built_from=$(echo "$subject" | sed -n 's/^Precompiled natives @\([0-9a-f]\{7,40\}\)$/\1/p')
if [ -z "$built_from" ]; then
    fail "the last change to ${NATIVES} (${natives_commit:0:10} \"${subject}\") is not a squashed \"Precompiled natives @<rev>\" commit from cross-compile.yml"
elif ! git merge-base --is-ancestor "$built_from" HEAD 2>/dev/null; then
    fail "natives were built from ${built_from}, which is not part of this branch"
else
    changed=$(git diff --name-only "$built_from" HEAD -- "${NATIVE_SOURCES[@]}")
    if [ -n "$changed" ]; then
        fail "native sources changed after the natives were built from ${built_from}: $(echo "$changed" | tr '\n' ' ')"
    else
        echo "Natives were built from ${built_from}; no native source changed since"
    fi
fi

if [ "$failures" -gt 0 ]; then
    echo "${failures} problem(s) with the precompiled natives"
    exit 1
fi
echo "Natives are fresh"
