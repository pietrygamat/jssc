

My personal alternative build setup because default build strategy does
not work for me.

This builds jssc into dedicated JARs each architecture. This is mainly
to exclude unwanted bloat never ever needed on specific systems.

Initially written for jssc 2.9.5.


## Configure for x86_64 on deb10

  && CXX="g++" \
  && CFLAGS="-fPIC -Wall -Wextra -Werror -pedantic \
      -Os -s \
      -Wno-error=long-long \
      -Wno-error=sign-compare \
      -Wno-error=variadic-macros \
      -Wno-long-long" \
  && PKGSTOADD="curl git g++ maven gdb patch zip unzip" \
  && SUDO=sudo \
  && JSSC_CPP=src/main/cpp/_nix_based/jssc.cpp \
  && LIBJSSC_SO=src/main/resources-precompiled/natives/linux_64/libjssc.so \
  && JNI_MD_INCDIR=~/.sdkman/candidates/java/current/include/linux \
  && export MAVEN_OPTS="" \


## Configure for x86_64 win on deb10

  && CXX="x86_64-w64-mingw32-g++" \
  && CFLAGS="-fPIC -Wall -Wextra -Werror -pedantic \
      -Os -s \
      -Wno-error=long-long \
      -Wno-error=sign-compare \
      -Wno-error=variadic-macros \
      -Wno-long-long" \
  && PKGSTOADD="curl git g++-mingw-w64-x86-64-win32 maven gdb patch zip unzip" \
  && SUDO=sudo \
  && JSSC_CPP=src/main/cpp/windows/jssc.cpp \
  && LIBJSSC_SO=src/main/resources-precompiled/natives/windows_64/jssc.dll \
  && JNI_MD_INCDIR=/opt/openjdk-windoof/include \
  && export MAVEN_OPTS="-DskipTests=true" \


## Setup

  && SDKMAN_VERSION=5.18.2 \
  && $SUDO apt install -y --no-install-recommends ${PKGSTOADD:?} \
  && curl -s "https://get.sdkman.io" | bash \
  && source "$HOME/.sdkman/bin/sdkman-init.sh" \
  && sdk install java 8.0.422-amzn \
  && $SUDO mkdir "/opt/openjdk-windoof" "/opt/openjdk-windoof/include" \
  && (cd "/opt/openjdk-windoof/include" && curl -sSL "https://github.com/openjdk/jdk/blob/master/src/java.base/windows/native/include/jni_md.h" | $SUDO tee jni_md.h >/dev/null) \


## Make

  && cat contrib/hiddenalpha-buildEnv-one/res/pom.patch | patch -p 1 \
  && mvn clean \
  && rm -rf src/main/resources-precompiled/natives/* \
  && mkdir src/main/resources-precompiled/natives/windows_64 \
  && mvn -PnoCmake test-compile \
  && PROJECT_VERSION="$(git describe --tags|sed 's,^v,,')" \
  && printf '%s "%s"\n' "#define JSSC_VERSION" "${PROJECT_VERSION:?}" \
      > src/main/cpp/version.h \
  && ${CXX} $CFLAGS -shared \
      -o ${LIBJSSC_SO:?}  \
      ${JSSC_CPP:?} \
      -I$HOME/.sdkman/candidates/java/current/include \
      -I${JNI_MD_INCDIR:?} \
      -Isrc/main/cpp \
  && for T in "windows_64"; do true \
    && mvn -PnoCmake -PnoJavah -PnativeJar -P"${T:?}" package \
    ;done \
  && mvn -PnoCmake -PnoJavah -PnoNatives -PwithTestClasspath verify \
  && SUMFILE="$(cd target && ls -d jssc-*|sort|tail -n1)" \
  && SUMFILE="${SUMFILE%.jar*}.sha256" \
  && (cd target && sha256sum -b jssc-*.jar > "${SUMFILE:?}") \


## gdb

For debugging, adjust CFLAGS to include debugger info and re-compile.

Then run this before starting gdb.

  && m2repo="/home/${USER:?}/.m2/repository" \
  && PROJDIR="/home/${USER:?}/jssc-work" \
  && export LD_LIBRARY_PATH="${PROJDIR:?}/src/main/resources-precompiled/natives/linux_64" \
  && export CLASSPATH="target/test-classes:target/classes\
:${m2repo:?}/org/slf4j/slf4j-api/1.7.30/slf4j-api-1.7.30.jar\
:${m2repo:?}/org/slf4j/slf4j-simple/1.7.30/slf4j-simple-1.7.30.jar\
:${m2repo:?}/org/scijava/native-lib-loader/2.4.0/native-lib-loader-2.4.0.jar\
:${m2repo:?}/junit/junit/4.12/junit-4.12.jar\
" \


