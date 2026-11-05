#!/bin/bash
# Checks a macOS libjssc.dylib built on Linux against the expected target and against a reference
# library (the precompiled one from src/main/resources-precompiled), using the LLVM binary tools:
#
#   check-macos-native.sh <new.dylib> <reference.dylib> <arch> <minos> <sdk>
#
#   arch   x86_64 or arm64
#   minos  the deployment target, e.g. 10.9
#   sdk    the SDK version the library must record, e.g. 10.9
#
# Fails on: wrong architecture; a deployment target or SDK other than the expected one; the wrong load
# command for it (LC_VERSION_MIN_MACOSX below 10.14, LC_BUILD_VERSION from 10.14 on, as Apple's ld64
# writes them); dependencies or imported symbols that differ from the reference (a different SDK or
# toolchain can make the library call functions old macOS versions don't have, which the deployment
# target alone does not show); JNI exports that differ from the reference; no JSSC-IDENT section;
# no code signature on arm64, where macOS refuses to load unsigned code.
#
# Tools: llvm-otool, llvm-nm, llvm-objdump on PATH (or LLVM_SUFFIX, e.g. -18). A summary table
# goes to stdout and, in GitHub Actions, to the job summary.
set -euo pipefail

new=$1; ref=$2; arch=$3; minos=$4; sdk=$5
OTOOL=llvm-otool${LLVM_SUFFIX:-}
NM=llvm-nm${LLVM_SUFFIX:-}
OBJDUMP=llvm-objdump${LLVM_SUFFIX:-}

problems=()
fail() { problems+=("$1"); }
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# "11" and "11.0" are the same version
norm() { case $1 in *.*) echo "$1" ;; '') ;; *) echo "$1.0" ;; esac; }

# cputype of the Mach-O header
cpu() { "$OTOOL" -hv "$1" | awk 'END { print $2 }'; }
# "<load command> <minos> <sdk>"
version_cmd() {
    "$OTOOL" -l "$1" | awk '
        $1 == "cmd" { if (found) exit; cmd = $2; found = (cmd == "LC_VERSION_MIN_MACOSX" || cmd == "LC_BUILD_VERSION") }
        found && ((cmd == "LC_VERSION_MIN_MACOSX" && $1 == "version") || $1 == "minos") { min = $2 }
        found && $1 == "sdk" { sdk = $2 }
        END { print cmd, min, sdk }'
}
deps() { "$OTOOL" -L "$1" | tail -n +3 | awk '{ print $1 }' | sort; }
imports() { "$NM" -u "$1" | awk '{ print $NF }' | sort -u; }
jni_exports() { "$NM" -gU "$1" | awk '{ print $NF }' | grep '^_Java_' | sort; }
load_cmds() { "$OTOOL" -l "$1" | awk '$1 == "cmd" { print $2 }' | sort | uniq -c | awk '{ print $2 ($1 > 1 ? "x" $1 : "") }' | tr '\n' ' '; }
has_section() { "$OBJDUMP" -h "$1" | awk -v s="$2" '$2 == s { found = 1 } END { exit !found }'; }

expected_cpu=$([ "$arch" = arm64 ] && echo ARM64 || echo X86_64)
[ "$(cpu "$new")" = "$expected_cpu" ] || fail "architecture is $(cpu "$new"), expected $expected_cpu"

read -r new_cmd new_minos new_sdk <<< "$(version_cmd "$new")"
read -r ref_cmd ref_minos ref_sdk <<< "$(version_cmd "$ref")"
[ "$(norm "$new_minos")" = "$(norm "$minos")" ] || fail "minos is ${new_minos:-missing}, expected $minos"
[ "$(norm "$new_sdk")" = "$(norm "$sdk")" ] || fail "sdk is ${new_sdk:-missing}, expected $sdk"
major=${minos%%.*}; minor=${minos#*.}; minor=${minor%%.*}
if [ "$major" -eq 10 ] && [ "$minor" -lt 14 ]; then expected_cmd=LC_VERSION_MIN_MACOSX; else expected_cmd=LC_BUILD_VERSION; fi
[ "$new_cmd" = "$expected_cmd" ] || fail "version load command is ${new_cmd:-missing}, expected $expected_cmd"

deps "$new" > "$tmp/new.deps"; deps "$ref" > "$tmp/ref.deps"
diff -u "$tmp/ref.deps" "$tmp/new.deps" > "$tmp/deps.diff" || fail "dependencies differ from the reference: $(grep '^[-+]/' "$tmp/deps.diff" | tr '\n' ' ')"

imports "$new" > "$tmp/new.imports"; imports "$ref" > "$tmp/ref.imports"
diff -u "$tmp/ref.imports" "$tmp/new.imports" > "$tmp/imports.diff" || fail "imported symbols differ from the reference: $(grep '^[-+]_' "$tmp/imports.diff" | tr '\n' ' ')"

jni_exports "$new" > "$tmp/new.jni"; jni_exports "$ref" > "$tmp/ref.jni"
diff -u "$tmp/ref.jni" "$tmp/new.jni" > "$tmp/jni.diff" || fail "JNI exports differ from the reference: $(grep '^[-+]_' "$tmp/jni.diff" | tr '\n' ' ')"

has_section "$new" __jssc || fail "no __TEXT,__jssc section (JSSC-IDENT)"
if [ "$arch" = arm64 ]; then
    "$OTOOL" -l "$new" | grep -q LC_CODE_SIGNATURE || fail "no LC_CODE_SIGNATURE: arm64 macOS does not load unsigned code"
fi

dep_versions() { "$OTOOL" -L "$1" | tail -n +3 | sed -E 's/^[[:space:]]+//; s/ \(compatibility version [^,]+, current version ([^)]+)\)/ \1/' | tr '\n' ';' | sed 's/;$//; s/;/; /g'; }
row() { printf '| %s | %s | %s |\n' "$1" "$2" "$3"; }
{
    echo "### $(basename "$(dirname "$new")")/$(basename "$new"): $([ ${#problems[@]} -eq 0 ] && echo OK || echo FAILED)"
    echo
    row "" "built" "reference"
    row "---" "---" "---"
    row "architecture" "$(cpu "$new")" "$(cpu "$ref")"
    row "version command" "$new_cmd" "$ref_cmd"
    row "minos" "$new_minos" "$ref_minos"
    row "sdk" "$new_sdk" "$ref_sdk"
    row "dependencies" "$(dep_versions "$new")" "$(dep_versions "$ref")"
    row "install name" "$("$OTOOL" -D "$new" | tail -1)" "$("$OTOOL" -D "$ref" | tail -1)"
    row "imported symbols" "$(wc -l < "$tmp/new.imports")" "$(wc -l < "$tmp/ref.imports")"
    row "JNI exports" "$(wc -l < "$tmp/new.jni")" "$(wc -l < "$tmp/ref.jni")"
    row "other exports" "$("$NM" -gU "$new" | awk '{ print $NF }' | grep -v '^_Java_' | tr '\n' ' ')" "$("$NM" -gU "$ref" | awk '{ print $NF }' | grep -v '^_Java_' | tr '\n' ' ')"
    row "load commands" "$(load_cmds "$new")" "$(load_cmds "$ref")"
    row "size" "$(wc -c < "$new")" "$(wc -c < "$ref")"
    echo
    for p in "${problems[@]}"; do echo "- :x: $p"; done
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

[ ${#problems[@]} -eq 0 ]
