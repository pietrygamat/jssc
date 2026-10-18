#!/usr/bin/env bash
# Cross-compiles jssc natives for the given targets in the jssc-cross image, one container per
# target, and copies them into src/main/resources-precompiled/natives/<target>.
#
#   builder/cross-compile.sh [target...] [-- extra maven args]
#
# Without targets, builds all of them. Environment:
#   IMAGE   image to use (default: localhost/jssc-cross-sdk:dev, see builder/README.md)
#   ENGINE  podman or docker (default: podman if installed)
#   M2      host directory or volume name for the Maven repository (default: volume jssc-m2)
set -euo pipefail
cd "$(dirname "$0")/.."

IMAGE=${IMAGE:-localhost/jssc-cross-sdk:dev}
ENGINE=${ENGINE:-$(command -v podman || command -v docker)}
M2=${M2:-jssc-m2}
ALL="linux_32 linux_64 linux_arm linux_arm64 linux_riscv32 linux_riscv64 linux_ppc
     windows_32 windows_64 windows_arm64 osx_64 osx_arm64"

targets=()
while [ $# -gt 0 ] && [ "$1" != "--" ]; do
    targets+=("$1")
    shift
done
[ "${1:-}" = "--" ] && shift
[ ${#targets[@]} -gt 0 ] || read -r -a targets <<< "$(echo $ALL)"

# jssc target -> maven profile and os.target.* properties (see ant/build.xml)
maven_args() {
    case $1 in
        linux_32)      echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=x86_32 -Dos.target.bitness=32" ;;
        linux_64)      echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=x86_64 -Dos.target.bitness=64" ;;
        linux_arm)     echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=arm_32 -Dos.target.bitness=32" ;;
        linux_arm64)   echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=aarch_64 -Dos.target.bitness=64" ;;
        linux_riscv32) echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=riscv32 -Dos.target.bitness=32" ;;
        linux_riscv64) echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=riscv64 -Dos.target.bitness=64" ;;
        linux_ppc)     echo "-P dockcross -Dos.target.name=linux -Dos.target.arch=ppc_64 -Dos.target.bitness=64" ;;
        windows_32)    echo "-P dockcross -Dos.target.name=windows -Dos.target.arch=x86_32 -Dos.target.bitness=32" ;;
        windows_64)    echo "-P dockcross -Dos.target.name=windows -Dos.target.arch=x86_64 -Dos.target.bitness=64" ;;
        windows_arm64) echo "-P dockcross -Dos.target.name=windows -Dos.target.arch=aarch_64 -Dos.target.bitness=64" ;;
        osx_64)        echo "-P osxcross -Dos.target.arch=x86_64" ;;
        osx_arm64)     echo "-P osxcross -Dos.target.arch=aarch_64" ;;
        *) echo "unknown target: $1 (known: $(echo $ALL))" >&2; return 1 ;;
    esac
}

for target in "${targets[@]}"; do
    args=$(maven_args "$target")
    echo "=== $target"
    # shellcheck disable=SC2086
    "$ENGINE" run --rm \
        --volume "$PWD:/work:Z" --volume "$M2:/root/.m2" --workdir /work \
        --env MAVEN_OPTS="${MAVEN_OPTS:-}" \
        "$IMAGE" \
        jssc-target "$target" \
        mvn -B clean install $args -P update-resources-precompiled "$@"
done
