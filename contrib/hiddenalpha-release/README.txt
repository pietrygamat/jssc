

## Configure

  && SUDO=sudo \
  && MVN="mvn -q" \
  && TRIPLET=x86_64-linux-gnu \
  && WORKDIR="/home/${USER:?}/work" \
  && PROJECT_VERSION="$(git describe --tags|sed -E 's,^v,,')" \


## Setup build machine

  && mkdir -p "${WORKDIR:?}" \
  && cd "${WORKDIR:?}" \
  && if false ;then true \
    ;elif test "${TRIPLET:?}" = "x86_64-linux-gnu" ;then true \
      && $SUDO apt update && $SUDO apt install --no-install-recommends -y \
             openjdk-17-jdk-headless maven g++ cmake make patch \
    ;else true \
      && printf '\nERROR: Target %s not impl.\n\n' "${TRIPLET:?}" && false \
    ;fi \


## Build

  && cd "${WORKDIR:?}" \
  && if false ;then true \
    ;elif test "${TRIPLET:?}" = "x86_64-linux-gnu" ;then true \
      && MVN_ARCH="x86_64" \
      && CLASSIFIER_OLD="-linux-x86_64-64" \
      && CLASSIFIER_NEW="-linux-x86_64" \
    ;else true \
      && printf '\nENOTSUP: %s\n\n' "${TRIPLET:?}" && false \
    ;fi  \
  && cat contrib/hiddenalpha-release/res/*.patch | patch -p1 --force \
  && ${MVN:?} versions:set -DgenerateBackupPoms=false -DallowSnapshots=true -DnewVersion="${PROJECT_VERSION:?}" \
  && `# see "https://github.com/java-native/jssc/blob/v2.9.6/.github/workflows/cross-compile.yml#L21" ` \
  && sed -i -E "s,(private +static +final +String +libVersion +=).*$,\1 \"${PROJECT_VERSION:?}\";," \
         "src/main/java/jssc/SerialNativeInterface.java" \
  && ${MVN:?} clean \
  && ${MVN:?} "-P${MVN_ARCH:?}" test \
  && ${MVN:?} "-P${MVN_ARCH:?}" -Ppack-java-only -DskipTests package \
  && jarBlanked="$(basename "$(ls -d target/jssc-*.jar|grep -v -E ".*${CLASSIFIER_OLD:?}")")" \
  && mv "target/${jarBlanked:?}" "target/pack-java-only.jar" `# hide from maven` \
  && ${MVN:?} "-P${MVN_ARCH:?}" -Ppack-native-only -DskipTests package \
  && jarClassifiedOld="$(basename "$(ls -d target/jssc-*.jar|grep -vE 'jssc-[^-]+(-[^-]+)?.jar')")" \
  && jarClassifiedNew="$(echo $jarClassifiedOld|sed -E 's,^(jssc-.*)'"${CLASSIFIER_OLD:?}"'(.jar)$,\1'"${CLASSIFIER_NEW:?}"'\2,')" \
  && mv "target/${jarClassifiedOld:?}" "target/${jarClassifiedNew:?}" \
  && mv "target/pack-java-only.jar" "target/${jarBlanked:?}" `# unhide` \


