#!/bin/bash
# Checks a freshly built libjssc.dylib (B-03): its deployment target, and what it imports
# compared to the previous release.
#
#   check-macos-dylib.sh <new.dylib> <expected-minos> [<reference.dylib>]
#
# - Fails when the deployment target (minos) differs from <expected-minos>. Apple notarization
#   needs >= 10.9 (#54, #187); a higher value drops users on older macOS.
# - Warns about every symbol the new library imports that <reference.dylib> (the previous release)
#   did not. A new SDK can make the compiler call libc++/libSystem functions that old macOS
#   versions don't have, which the deployment target alone does not reveal. A warning, because
#   a legitimate code change also adds imports; check each one against the oldest supported macOS.
#
# Runs on macOS, or on Linux with OTOOL=llvm-otool NM=llvm-nm.
set -euo pipefail

OTOOL=${OTOOL:-otool}
NM=${NM:-nm}

new=$1
expected=$2
reference=${3:-}

minos() {
    # LC_BUILD_VERSION has "minos X"; older load command LC_VERSION_MIN_MACOSX has "version X"
    "$OTOOL" -l "$1" | awk '
        /LC_BUILD_VERSION/      { cmd = "build" }
        /LC_VERSION_MIN_MACOSX/ { cmd = "min" }
        cmd == "build" && $1 == "minos"   { print $2; exit }
        cmd == "min"   && $1 == "version" { print $2; exit }'
}

sdk() {
    "$OTOOL" -l "$1" | awk '/LC_BUILD_VERSION|LC_VERSION_MIN_MACOSX/ { found = 1 } found && $1 == "sdk" { print $2; exit }'
}

actual=$(minos "$new")
echo "${new}: minos ${actual:-?}, sdk $(sdk "$new"), expected minos ${expected}"
echo "Linked libraries:"
"$OTOOL" -L "$new" | tail -n +2

status=0
if [ "$actual" != "$expected" ]; then
    echo "::error file=${new}::deployment target is ${actual:-missing}, expected ${expected} (MACOSX_DEPLOYMENT_TARGET not applied?)"
    status=1
fi

if [ -n "$reference" ] && [ -f "$reference" ]; then
    new_imports=$(comm -13 <("$NM" -u -j "$reference" | sort -u) <("$NM" -u -j "$new" | sort -u))
    if [ -n "$new_imports" ]; then
        echo "::warning file=${new}::imports symbols the previous release did not; check that macOS ${expected} provides them: $(echo "$new_imports" | tr '\n' ' ')"
    else
        echo "No new imported symbols compared to ${reference}"
    fi
fi

exit $status
