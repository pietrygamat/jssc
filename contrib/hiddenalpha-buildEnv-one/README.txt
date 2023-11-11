

My personal alternative build setup because default build strategy does
not work for me.

This builds jssc into dedicated JARs each architecture. This is mainly
to exclude unwanted bloat never ever needed on specific systems.

Initially written for jssc 2.9.5.


true `# Configure for debian 9 x86_64` \
  && CXX="g++" \
  && CFLAGS="-fPIC -Wall -pedantic -Werror \
      -ggdb -O0 \
      -Wno-error=long-long \
      -Wno-error=sign-compare \
      -Wno-error=variadic-macros \
      -Wno-long-long" \
  && targets="linux_64" \
  && SUDO=sudo \
  && true


(true `# Setup` \
  && $SUDO apt install -y --no-install-recommends \
        git openjdk-8-jdk-headless g++ maven gdb \
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


## gdb

true `# run this before starting gdb` \
  && m2repo="/home/${USER:?}/.m2/repository" \
  && PROJDIR="/home/${USER:?}/jssc-work" \
  && export LD_LIBRARY_PATH="${PROJDIR:?}/src/main/resources-precompiled/natives/linux_64" \
  && export CLASSPATH="target/test-classes:target/classes\
:${m2repo:?}/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar\
:${m2repo:?}/org/slf4j/slf4j-simple/1.7.30/slf4j-simple-1.7.30.jar\
:${m2repo:?}/org/scijava/native-lib-loader/2.4.0/native-lib-loader-2.4.0.jar\
" \
  && true

