

My personal alternative build setup because default build strategy does
not work for me.

This builds jssc into dedicated JARs each architecture. This is mainly
to exclude unwanted bloat never ever needed on specific systems.

Initially written for jssc 2.9.5.


true `# Configure for debian 9 x86_64` \
  && CXX="g++" \
  && CFLAGS="-fPIC -Wall -pedantic -Werror \
      -Wno-error=long-long \
      -Wno-error=sign-compare \
      -Wno-error=variadic-macros \
      -Wno-long-long" \
  && targets="linux_64" \
  && SUDO=sudo \
  && true


(true `# Setup` \
  && $SUDO apt install -y --no-install-recommends \
        git openjdk-8-jdk-headless g++ maven \
  && true)


(true `# Make` \
  && set -e \
  && if test -n $BASH_VERSINFO; then set -o posix; fi \
  && cat contrib/hiddenalpha-buildEnv-one/res/pom.patch | git apply \
  && mvn clean \
  && mvn -PnoCmake compile \
  && printf '%s "%s"\n' "#define JSSC_VERSION" "$(git describe --tags|sed 's,^v,,')" \
      > src/main/cpp/version.h \
  && mkdir -p src/main/resources-precompiled/natives/linux_64 \
  && g++ $CFLAGS -shared \
      -o src/main/resources-precompiled/natives/linux_64/libjssc.so \
      src/main/cpp/_nix_based/jssc.cpp \
      -I/usr/lib/jvm/java-1.8.0-openjdk-amd64/include \
      -I/usr/lib/jvm/java-1.8.0-openjdk-amd64/include/linux \
      -Isrc/main/cpp \
  && for T in ${targets:?}; do true \
    && mvn -PnoCmake -PnoJavah -PnativeJar -P"${T:?}" package \
    ;done \
  && mvn -PnoCmake -PnoJavah -PnoNatives -PwithTestClasspath verify \
  && true)


