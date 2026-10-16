#!/bin/bash
# Release gate: do the natives still load on the oldest systems jssc supports? (B-02)
#
#   check-natives-compat.sh [natives-dir]
#
# Reads every shipped library without loading it and fails when
#  - a Linux library needs a newer GLIBC symbol version than its limit below (#190: dockcross
#    "latest" silently raised the floor to 2.35), or links anything but the C library itself,
#  - a macOS library has a deployment target (minos) other than its limit (#187: 10.8 broke
#    notarization) or links anything but libSystem/libc++/IOKit/CoreFoundation,
#  - a Windows DLL needs a newer Windows than its limit (PE subsystem version), or imports a DLL
#    that old Windows lacks (a dynamically linked libstdc++/libgcc/winpthread/UCRT),
#  - a library is built for the wrong CPU or bitness,
#  - a directory has no entry in the table below.
#
# The limits are a ratchet: they are what the 2.10.4 natives need. Lowering one is welcome;
# raising one must be a deliberate decision, recorded in the commit that changes this table.
# Needs binutils readelf and llvm-objdump.
set -uo pipefail

NATIVES=${1:-src/main/resources-precompiled/natives}
OBJDUMP=${OBJDUMP:-$(command -v llvm-objdump || ls /usr/bin/llvm-objdump-* 2>/dev/null | sort -V | tail -1)}

# directory     file             expected format (readelf Machine / llvm-objdump "file format")   limit
LIMITS="
linux_32        libjssc.so       ELF32:Intel 80386                          2.28
linux_64        libjssc.so       ELF64:Advanced Micro Devices X86-64        2.14
linux_arm       libjssc.so       ELF32:ARM                                  2.28
linux_armhf     libjssc.so       ELF32:ARM                                  2.28
linux_armsf     libjssc.so       ELF32:ARM                                  2.32
linux_arm64     libjssc.so       ELF64:AArch64                              2.17
linux_ppc       libjssc.so       ELF64:PowerPC64                            2.34
linux_riscv32   libjssc.so       ELF32:RISC-V                               2.34
linux_riscv64   libjssc.so       ELF64:RISC-V                               2.34
osx_64          libjssc.dylib    mach-o 64-bit x86-64                       10.9
osx_arm64       libjssc.dylib    mach-o arm64                               11.0
windows_32      jssc.dll         coff-i386                                  5.1
windows_64      jssc.dll         coff-x86-64                                5.2
windows_arm64   jssc.dll         coff-arm64                                 10.0
"
# linux_armhf/linux_armsf only exist once the b10-native-loader branch lands (#35); their limits
# are what local dockcross builds of linux-armv7 / linux-armv5 needed (BACKLOG B-11).

LINUX_LIBS='^(libc\.so\.6|libm\.so\.6|libdl\.so\.2|libpthread\.so\.0|librt\.so\.1|ld-linux[-a-z0-9_.]*\.so\.[0-9]+|ld64\.so\.[0-9]+|ld\.so\.[0-9]+)$'
MACOS_LIBS='^(@rpath/libjssc\.dylib|/usr/lib/libSystem\.B\.dylib|/usr/lib/libc\+\+\.1\.dylib|/System/Library/Frameworks/(IOKit|CoreFoundation)\.framework/.*)$'
WINDOWS_LIBS='^(kernel32|advapi32|msvcrt)\.dll$'

failures=0
fail() {
    echo "::error::$*"
    failures=$((failures + 1))
}
version_gt() { # is $1 > $2, comparing dotted numbers
    [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" = "$1" ]
}

check_elf() { # file, "ELFnn:Machine", max glibc
    local file=$1 class=${2%%:*} machine=${2#*:} limit=$3
    local header; header=$(readelf -h "$file")
    grep -q "Class: *${class}$" <<< "$header" || fail "${file}: not ${class}"
    grep -q "Machine: *${machine}$" <<< "$header" || fail "${file}: machine is $(sed -n 's/ *Machine: *//p' <<< "$header"), expected ${machine}"

    local versions; versions=$(readelf -V --wide "$file" | grep -o 'Name: [A-Z_]*[0-9.]*' | sed 's/Name: //' | sort -u)
    local other; other=$(grep -v '^GLIBC_[0-9.]*$' <<< "$versions" | grep -v '^GLIBC_PRIVATE$' | grep -v '^$')
    [ -z "$other" ] || fail "${file}: needs $(echo "$other" | tr '\n' ' ')(libstdc++/libgcc must be linked statically)"
    local max; max=$(grep -o '^GLIBC_[0-9.]*$' <<< "$versions" | sed 's/GLIBC_//' | sort -V | tail -1)
    if [ -n "$max" ] && version_gt "$max" "$limit"; then
        fail "${file}: needs GLIBC ${max}, limit is ${limit}; symbols: $(readelf --dyn-syms --wide "$file" | grep -o "[^ ]*@GLIBC_${max}" | sort -u | tr '\n' ' ')"
    fi
    for lib in $(readelf -d "$file" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p'); do
        [[ $lib =~ $LINUX_LIBS ]] || fail "${file}: links ${lib}"
    done
    echo "  ${file}: ${class} ${machine}, GLIBC ${max:-none} (limit ${limit})"
}

check_macho() { # file, "mach-o ...", expected minos
    local file=$1 format=$2 limit=$3
    # llvm-objdump -f prints the format, then fails on the Mach-O dylib it cannot disassemble
    local header; header=$("$OBJDUMP" -f "$file" 2>/dev/null || true)
    grep -q "file format ${format}$" <<< "$header" || fail "${file}: not ${format}"
    local minos; minos=$("$OBJDUMP" --macho --private-headers "$file" | awk '
        /LC_BUILD_VERSION/      { cmd = "build" }
        /LC_VERSION_MIN_MACOSX/ { cmd = "min" }
        cmd == "build" && $1 == "minos"   { print $2; exit }
        cmd == "min"   && $1 == "version" { print $2; exit }')
    [ "$minos" = "$limit" ] || fail "${file}: deployment target ${minos:-missing}, expected ${limit}"
    for lib in $("$OBJDUMP" --macho --dylibs-used "$file" | tail -n +2 | awk '{ print $1 }'); do
        [[ $lib =~ $MACOS_LIBS ]] || fail "${file}: links ${lib}"
    done
    echo "  ${file}: ${format}, minos ${minos} (expected ${limit})"
}

check_pe() { # file, "coff-...", max subsystem version
    local file=$1 format=$2 limit=$3
    local headers; headers=$("$OBJDUMP" -p "$file")
    grep -q "file format ${format}$" <<< "$headers" || fail "${file}: not ${format}"
    local subsystem
    subsystem="$(awk '$1 == "MajorSubsystemVersion" { print $2 }' <<< "$headers").$(awk '$1 == "MinorSubsystemVersion" { print $2 }' <<< "$headers")"
    if version_gt "$subsystem" "$limit"; then
        fail "${file}: needs Windows ${subsystem} (PE subsystem version), limit is ${limit}"
    fi
    for lib in $(sed -n 's/ *DLL Name: //p' <<< "$headers"); do
        [[ ${lib,,} =~ $WINDOWS_LIBS ]] || fail "${file}: imports ${lib}"
    done
    echo "  ${file}: ${format}, subsystem ${subsystem} (limit ${limit})"
}

[ -x "$OBJDUMP" ] || { echo "::error::llvm-objdump not found (apt-get install llvm)"; exit 1; }

for dir in "$NATIVES"/*/; do
    dir=$(basename "$dir")
    row=$(awk -v d="$dir" '$1 == d' <<< "$LIMITS")
    if [ -z "$row" ]; then
        fail "${NATIVES}/${dir}: no compatibility limit; add it to $0"
        continue
    fi
    name=$(awk '{ print $2 }' <<< "$row")
    format=$(awk '{ $1 = ""; $2 = ""; $NF = ""; sub(/^ +/, ""); sub(/ +$/, ""); print }' <<< "$row")
    limit=$(awk '{ print $NF }' <<< "$row")
    file="${NATIVES}/${dir}/${name}"
    if [ ! -f "$file" ]; then
        fail "${file} is missing"
        continue
    fi
    case $name in
        *.so)    check_elf "$file" "$format" "$limit" ;;
        *.dylib) check_macho "$file" "$format" "$limit" ;;
        *.dll)   check_pe "$file" "$format" "$limit" ;;
    esac
done

if [ "$failures" -gt 0 ]; then
    echo "${failures} compatibility problem(s)"
    exit 1
fi
echo "All natives are within their compatibility limits"
